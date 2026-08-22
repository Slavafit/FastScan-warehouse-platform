import {
  BadRequestException,
  Injectable,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHash, randomBytes, randomInt } from 'node:crypto';
import { hash } from 'bcryptjs';
import { PrismaService } from './prisma.service.js';

type TelegramMessage = {
  chat?: { id?: number };
  from?: { id?: number };
  text?: string;
};

@Injectable()
export class TelegramService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly config: ConfigService,
  ) {}

  info() {
    const username = this.botUsername();
    return {
      enabled: this.enabled(),
      bot_username: username,
      bot_url: username ? `https://t.me/${username}` : null,
    };
  }

  enabled() {
    return Boolean(this.config.get<string>('TELEGRAM_BOT_TOKEN'));
  }

  webhookSecretIsValid(value?: string) {
    const expected = this.config.get<string>('TELEGRAM_WEBHOOK_SECRET');
    return Boolean(expected && value && expected === value);
  }

  async createLinkCode(clientAccountId: string) {
    if (!this.enabled())
      throw new ServiceUnavailableException('Telegram bot is not configured');
    const code = randomInt(100000, 1000000).toString();
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000);
    await this.prisma.telegramLinkToken.upsert({
      where: { clientAccountId },
      update: { codeHash: this.digest(code), expiresAt },
      create: { clientAccountId, codeHash: this.digest(code), expiresAt },
    });
    const username = this.botUsername();
    return {
      code,
      expires_at: expiresAt.toISOString(),
      bot_username: username,
      deep_link: username
        ? `https://t.me/${username}?start=link_${code}`
        : null,
    };
  }

  async unlink(clientAccountId: string) {
    await this.prisma.$transaction([
      this.prisma.telegramLinkToken.deleteMany({ where: { clientAccountId } }),
      this.prisma.clientAccount.update({
        where: { id: clientAccountId },
        data: {
          telegramUserId: null,
          telegramChatId: null,
          preferredNotificationChannel: 'INTERNAL',
        },
      }),
    ]);
  }

  async handleMessage(message?: TelegramMessage) {
    const text = message?.text?.trim();
    const chatId = message?.chat?.id;
    const userId = message?.from?.id;
    if (!text || chatId === undefined || userId === undefined) return;

    const linkCode = this.linkCode(text);
    if (linkCode) {
      if (await this.linkAccount(linkCode, userId, chatId))
        await this.sendMessage(
          chatId,
          'Telegram привязан к вашему кабинету FastScan.',
        );
      return;
    }

    if (
      /^\/reset(?:@\w+)?$/i.test(text) ||
      /^\/start(?:@\w+)?\s+reset$/i.test(text)
    ) {
      await this.requestPasswordReset(userId, chatId);
      return;
    }

    await this.sendMessage(
      chatId,
      'FastScan: привяжите Telegram кодом из настроек кабинета. Для восстановления пароля отправьте /reset.',
    );
  }

  async sendMessage(chatId: bigint | number, text: string) {
    const token = this.config.get<string>('TELEGRAM_BOT_TOKEN');
    if (!token) return false;
    const response = await fetch(
      `https://api.telegram.org/bot${token}/sendMessage`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ chat_id: chatId.toString(), text }),
      },
    );
    return response.ok;
  }

  async confirmPasswordReset(rawToken: string, password: string) {
    const now = new Date();
    const token = await this.prisma.passwordResetToken.findUnique({
      where: { tokenHash: this.digest(rawToken) },
    });
    if (!token || token.usedAt || token.expiresAt <= now)
      throw new BadRequestException(
        'Password reset link is invalid or expired',
      );
    const passwordHash = await hash(password, 12);
    await this.prisma.$transaction(async (transaction) => {
      const claimed = await transaction.passwordResetToken.updateMany({
        where: { id: token.id, usedAt: null, expiresAt: { gt: now } },
        data: { usedAt: now },
      });
      if (claimed.count !== 1)
        throw new BadRequestException(
          'Password reset link is invalid or expired',
        );
      await transaction.user.update({
        where: { id: token.userId },
        data: {
          passwordHash,
          tokenVersion: { increment: 1 },
        },
      });
      await transaction.passwordResetToken.updateMany({
        where: { userId: token.userId, usedAt: null },
        data: { usedAt: now },
      });
    });
  }

  private async linkAccount(
    code: string,
    telegramUserId: number,
    chatId: number,
  ) {
    const token = await this.prisma.telegramLinkToken.findUnique({
      where: { codeHash: this.digest(code) },
    });
    if (!token || token.expiresAt <= new Date()) {
      await this.sendMessage(
        chatId,
        'Код привязки недействителен или истёк. Создайте новый код в кабинете.',
      );
      return false;
    }
    const occupied = await this.prisma.clientAccount.findUnique({
      where: { telegramUserId: BigInt(telegramUserId) },
    });
    if (occupied && occupied.id !== token.clientAccountId) {
      await this.sendMessage(
        chatId,
        'Этот Telegram уже привязан к другому кабинету.',
      );
      return false;
    }
    await this.prisma.$transaction([
      this.prisma.clientAccount.update({
        where: { id: token.clientAccountId },
        data: {
          telegramUserId: BigInt(telegramUserId),
          telegramChatId: BigInt(chatId),
        },
      }),
      this.prisma.telegramLinkToken.delete({ where: { id: token.id } }),
    ]);
    return true;
  }

  private async requestPasswordReset(telegramUserId: number, chatId: number) {
    const account = await this.prisma.clientAccount.findUnique({
      where: { telegramUserId: BigInt(telegramUserId) },
      include: { user: true },
    });
    if (!account || !account.user.enabled) {
      await this.sendMessage(
        chatId,
        'Telegram не привязан к активному кабинету FastScan.',
      );
      return;
    }
    const rawToken = randomBytes(32).toString('base64url');
    const expiresAt = new Date(Date.now() + 15 * 60 * 1000);
    await this.prisma.$transaction([
      this.prisma.passwordResetToken.updateMany({
        where: { userId: account.userId, usedAt: null },
        data: { usedAt: new Date() },
      }),
      this.prisma.passwordResetToken.create({
        data: {
          userId: account.userId,
          tokenHash: this.digest(rawToken),
          expiresAt,
        },
      }),
    ]);
    const webUrl = this.config
      .get<string>('WEB_APP_URL', 'http://localhost:3000')
      .replace(/\/$/, '');
    await this.sendMessage(
      chatId,
      `Ссылка для смены пароля FastScan (действует 15 минут):\n${webUrl}/?reset_token=${rawToken}`,
    );
  }

  private linkCode(text: string) {
    const command = text.match(/^\/link(?:@\w+)?\s+(\d{6})$/i)?.[1];
    const start = text.match(/^\/start(?:@\w+)?\s+link_(\d{6})$/i)?.[1];
    return command ?? start ?? null;
  }

  private digest(value: string) {
    return createHash('sha256').update(value).digest('hex');
  }

  private botUsername() {
    return (
      this.config.get<string>('TELEGRAM_BOT_USERNAME')?.replace(/^@/, '') ||
      null
    );
  }
}
