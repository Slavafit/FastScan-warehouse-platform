import {
  Body,
  Controller,
  Get,
  NotFoundException,
  Param,
  Patch,
  Put,
  Query,
  Req,
} from '@nestjs/common';
import { ApiBearerAuth } from '@nestjs/swagger';
import { ProfileDto } from './dto.js';
import { AuthUser, Roles } from './auth.js';
import { Prisma, Role } from './generated/prisma/client.js';
import { PrismaService } from './prisma.service.js';

@ApiBearerAuth()
@Roles(Role.CLIENT)
@Controller('api/client')
export class ClientController {
  constructor(private readonly prisma: PrismaService) {}

  @Get('profile')
  async profile(@Req() request: { user: AuthUser }) {
    return this.profileResponse(await this.account(request.user.sub));
  }

  @Put('profile')
  async update(@Req() request: { user: AuthUser }, @Body() dto: ProfileDto) {
    const account = await this.account(request.user.sub);
    const updated = await this.prisma.clientAccount.update({
      where: { id: account.id },
      data: {
        phone: this.optionalText(dto.phone),
        address: this.optionalText(dto.address),
        emailNotifications: dto.email_notifications,
        pushNotifications: dto.push_notifications,
        receiver: {
          update: {
            firstName: dto.first_name.trim(),
            lastName: dto.last_name.trim(),
          },
        },
      },
      include: { receiver: true },
    });
    return this.profileResponse(updated);
  }

  @Get('orders')
  async orders(@Req() request: { user: AuthUser }) {
    const account = await this.account(request.user.sub);
    const orders = await this.prisma.shipmentOrder.findMany({
      where: { receiverId: account.receiverId },
      include: { box: true },
      orderBy: { createdAt: 'desc' },
    });
    return orders.map((order) => ({
      guid: order.id,
      tracking_number: order.trackingNumber,
      status_name: order.statusName,
      gross_weight: this.number(order.grossWeight),
      with_box: order.withBox !== 0,
      box_tracking_code: order.box?.trackingCode ?? null,
      created_at: order.createdAt.toISOString(),
      updated_at: order.updatedAt.toISOString(),
    }));
  }

  @Get('analytics')
  async analytics(@Req() request: { user: AuthUser }) {
    const account = await this.account(request.user.sub);
    const [orders, unread] = await Promise.all([
      this.prisma.shipmentOrder.findMany({
        where: { receiverId: account.receiverId },
        select: { grossWeight: true, boxId: true, statusName: true },
      }),
      this.prisma.clientNotification.count({
        where: { clientAccountId: account.id, readAt: null },
      }),
    ]);
    const statuses: Record<string, number> = {};
    for (const order of orders)
      statuses[order.statusName] = (statuses[order.statusName] ?? 0) + 1;
    const packed = orders.filter((order) => order.boxId).length;
    return {
      total_orders: orders.length,
      packed_orders: packed,
      unpacked_orders: orders.length - packed,
      total_weight: orders.reduce(
        (sum, order) => sum + (this.number(order.grossWeight) ?? 0),
        0,
      ),
      boxes_count: new Set(orders.map((order) => order.boxId).filter(Boolean))
        .size,
      status_breakdown: statuses,
      unread_notifications: unread,
    };
  }

  @Get('notifications')
  async notifications(
    @Req() request: { user: AuthUser },
    @Query('unreadOnly') unreadOnly = 'false',
  ) {
    const account = await this.account(request.user.sub);
    const notifications = await this.prisma.clientNotification.findMany({
      where: {
        clientAccountId: account.id,
        ...(unreadOnly === 'true' ? { readAt: null } : {}),
      },
      orderBy: { createdAt: 'desc' },
    });
    return notifications.map((item) => ({
      guid: item.id,
      type: item.type,
      title: item.title,
      message: item.message,
      read: item.readAt !== null,
      created_at: item.createdAt.toISOString(),
      read_at: item.readAt?.toISOString() ?? null,
    }));
  }

  @Patch('notifications/read-all')
  async readAll(@Req() request: { user: AuthUser }) {
    const account = await this.account(request.user.sub);
    await this.prisma.clientNotification.updateMany({
      where: { clientAccountId: account.id, readAt: null },
      data: { readAt: new Date() },
    });
    return { success: true };
  }

  @Patch('notifications/:guid/read')
  async read(@Req() request: { user: AuthUser }, @Param('guid') guid: string) {
    const account = await this.account(request.user.sub);
    const found = await this.prisma.clientNotification.findFirst({
      where: { id: guid, clientAccountId: account.id },
    });
    if (!found) throw new NotFoundException('Notification not found');
    const item = await this.prisma.clientNotification.update({
      where: { id: guid },
      data: { readAt: found.readAt ?? new Date() },
    });
    return {
      guid: item.id,
      type: item.type,
      title: item.title,
      message: item.message,
      read: true,
      created_at: item.createdAt.toISOString(),
      read_at: item.readAt?.toISOString() ?? null,
    };
  }

  private async account(userId: string) {
    const account = await this.prisma.clientAccount.findUnique({
      where: { userId },
      include: { receiver: true },
    });
    if (!account) throw new NotFoundException('Client account not found');
    return account;
  }

  private profileResponse(
    account: Awaited<ReturnType<ClientController['account']>>,
  ) {
    return {
      guid: account.id,
      client_code: account.receiver.clientCode,
      email: account.email,
      phone: account.phone,
      address: account.address,
      first_name: account.receiver.firstName,
      last_name: account.receiver.lastName,
      full_name:
        `${account.receiver.firstName} ${account.receiver.lastName}`.trim(),
      package_shoes: account.receiver.packageShoes,
      bag_package: account.receiver.bagPackage,
      email_notifications: account.emailNotifications,
      push_notifications: account.pushNotifications,
      created_at: account.createdAt.toISOString(),
    };
  }

  private optionalText(value?: string) {
    const normalized = value?.trim();
    return normalized ? normalized : null;
  }

  private number(value: Prisma.Decimal | null) {
    return value == null ? null : Number(value.toString());
  }
}
