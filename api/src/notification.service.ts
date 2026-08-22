import { Injectable } from '@nestjs/common';
import { NotificationType } from './generated/prisma/client.js';
import { PrismaService } from './prisma.service.js';
import { TelegramService } from './telegram.service.js';

@Injectable()
export class NotificationService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly telegram: TelegramService,
  ) {}

  async createForAccount(
    clientAccountId: string,
    type: NotificationType,
    title: string,
    message: string,
  ) {
    const notification = await this.prisma.clientNotification.create({
      data: { clientAccountId, type, title, message },
    });
    const account = await this.prisma.clientAccount.findUnique({
      where: { id: clientAccountId },
      select: { preferredNotificationChannel: true, telegramChatId: true },
    });
    if (
      account?.preferredNotificationChannel === 'TELEGRAM' &&
      account.telegramChatId !== null
    ) {
      try {
        await this.telegram.sendMessage(
          account.telegramChatId,
          `${title}\n${message}`,
        );
      } catch {
        // Internal delivery is authoritative; external channel failure is non-blocking.
      }
    }
    return notification;
  }

  async createForReceiver(
    receiverId: string,
    type: NotificationType,
    title: string,
    message: string,
  ) {
    const account = await this.prisma.clientAccount.findUnique({
      where: { receiverId },
    });
    if (!account) return null;
    return this.createForAccount(account.id, type, title, message);
  }
}
