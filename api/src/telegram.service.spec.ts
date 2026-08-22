import { BadRequestException } from '@nestjs/common';
import { TelegramService } from './telegram.service.js';

describe('TelegramService', () => {
  const config = {
    get: jest.fn((key: string, fallback?: string) => {
      const values: Record<string, string> = {
        TELEGRAM_BOT_TOKEN: 'test-token',
        TELEGRAM_BOT_USERNAME: 'FastScanTestBot',
        WEB_APP_URL: 'http://localhost:3000',
      };
      return values[key] ?? fallback;
    }),
  };

  it('does not report a successful link when the code is invalid', async () => {
    const prisma = {
      telegramLinkToken: { findUnique: jest.fn().mockResolvedValue(null) },
    };
    const service = new TelegramService(prisma as never, config as never);
    const send = jest.spyOn(service, 'sendMessage').mockResolvedValue(true);

    await service.handleMessage({
      chat: { id: 100 },
      from: { id: 200 },
      text: '/link 123456',
    });

    expect(send).toHaveBeenCalledTimes(1);
    expect(send.mock.calls[0][1]).toContain('недействителен');
  });

  it('changes the password, consumes reset links, and increments token version', async () => {
    type UserUpdate = {
      where: { id: string };
      data: { passwordHash: string; tokenVersion: { increment: number } };
    };
    let userUpdateArguments: UserUpdate | undefined;
    const userUpdate = jest.fn((arguments_: UserUpdate) => {
      userUpdateArguments = arguments_;
      return Promise.resolve({});
    });
    const tokenUpdateMany = jest.fn().mockResolvedValue({ count: 1 });
    const transaction = {
      passwordResetToken: {
        findUnique: jest.fn().mockResolvedValue({
          id: 'reset-1',
          userId: 'user-1',
          usedAt: null,
          expiresAt: new Date(Date.now() + 60_000),
        }),
        updateMany: tokenUpdateMany,
      },
      user: { update: userUpdate },
    };
    const prisma = {
      ...transaction,
      $transaction: jest.fn(
        async (callback: (client: typeof transaction) => Promise<unknown>) =>
          callback(transaction),
      ),
    };
    const service = new TelegramService(prisma as never, config as never);

    await service.confirmPasswordReset('one-time-token', 'new-password');

    expect(userUpdate).toHaveBeenCalledTimes(1);
    expect(userUpdateArguments?.where).toEqual({ id: 'user-1' });
    expect(userUpdateArguments?.data.tokenVersion).toEqual({ increment: 1 });
    expect(userUpdateArguments?.data.passwordHash).not.toBe('new-password');
    expect(tokenUpdateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { userId: 'user-1', usedAt: null },
      }),
    );
  });

  it('rejects an expired reset link', async () => {
    const prisma = {
      passwordResetToken: {
        findUnique: jest.fn().mockResolvedValue({
          userId: 'user-1',
          usedAt: null,
          expiresAt: new Date(Date.now() - 1),
        }),
      },
    };
    const service = new TelegramService(prisma as never, config as never);

    await expect(
      service.confirmPasswordReset('expired', 'new-password'),
    ).rejects.toBeInstanceOf(BadRequestException);
  });
});
