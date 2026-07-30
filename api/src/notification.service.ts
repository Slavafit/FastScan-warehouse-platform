import { Injectable } from '@nestjs/common';
import { NotificationType } from './generated/prisma/client.js';
import { PrismaService } from './prisma.service.js';

@Injectable()
export class NotificationService {
  constructor(private readonly prisma: PrismaService) {}

  createForAccount(
    clientAccountId: string,
    type: NotificationType,
    title: string,
    message: string,
  ) {
    return this.prisma.clientNotification.create({
      data: { clientAccountId, type, title, message },
    });
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
