import {
  BadRequestException,
  Body,
  ConflictException,
  Controller,
  Get,
  Post,
  Put,
  Query,
} from '@nestjs/common';
import { ApiBearerAuth } from '@nestjs/swagger';
import { hash } from 'bcryptjs';
import { NotificationDto, PlatformSettingsDto, StaffUserDto } from './dto.js';
import { NotificationType, Role } from './generated/prisma/client.js';
import { Roles } from './auth.js';
import { NotificationService } from './notification.service.js';
import { PrismaService } from './prisma.service.js';

@ApiBearerAuth()
@Roles(Role.ADMIN)
@Controller('api/admin')
export class AdminController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly notifications: NotificationService,
  ) {}

  @Get('users')
  async users() {
    return this.prisma.user.findMany({
      where: { role: { in: [Role.ADMIN, Role.WORKER] } },
      select: {
        id: true,
        login: true,
        role: true,
        enabled: true,
        createdAt: true,
      },
      orderBy: { createdAt: 'desc' },
    });
  }

  @Post('users')
  async createUser(@Body() dto: StaffUserDto) {
    if (dto.role !== Role.WORKER) {
      throw new BadRequestException('Only WORKER can be created');
    }
    const login = dto.login.trim().toLowerCase();
    if (await this.prisma.user.findUnique({ where: { login } })) {
      throw new ConflictException('User already exists');
    }
    const user = await this.prisma.user.create({
      data: {
        login,
        passwordHash: await hash(dto.password, 12),
        role: dto.role,
      },
    });
    return {
      success: true,
      guid: user.id,
      login: user.login,
      role: user.role,
      enabled: user.enabled,
    };
  }

  @Get('client-code-settings')
  async codeSettings() {
    const config = await this.prisma.clientCodeConfig.findUnique({
      where: { id: 1 },
    });
    const prefix = config?.codePrefix ?? 'KG';
    return {
      prefix,
      warehouse: config?.warehouseName ?? 'BMG',
      country: config?.country ?? 'España',
      code_format: `${prefix}######`,
    };
  }

  @Put('client-code-settings')
  async updateCodeSettings(@Body() dto: PlatformSettingsDto) {
    const prefix = dto.prefix.trim().toUpperCase();
    const warehouse = dto.warehouse.trim();
    const country = dto.country.trim();
    if (!/^[A-Z0-9-]{1,12}$/.test(prefix)) throw new Error('Invalid prefix');
    await this.prisma.clientCodeConfig.upsert({
      where: { id: 1 },
      update: { codePrefix: prefix, warehouseName: warehouse, country },
      create: {
        id: 1,
        codePrefix: prefix,
        warehouseName: warehouse,
        country,
      },
    });
    return {
      prefix,
      warehouse,
      country,
      code_format: `${prefix}######`,
    };
  }

  @Post('notifications')
  async dispatch(@Body() dto: NotificationDto) {
    const accounts = dto.client_code
      ? await this.prisma.clientAccount.findMany({
          where: {
            receiver: { clientCode: dto.client_code.trim().toUpperCase() },
          },
        })
      : await this.prisma.clientAccount.findMany();
    const type = Object.values(NotificationType).includes(
      dto.type as NotificationType,
    )
      ? (dto.type as NotificationType)
      : NotificationType.INFO;
    await Promise.all(
      accounts.map((account) =>
        this.notifications.createForAccount(
          account.id,
          type,
          dto.title.trim(),
          dto.message.trim(),
        ),
      ),
    );
    return { success: true, recipients_count: accounts.length };
  }

  @Get('operations')
  async operations(@Query('limit') limit = '100') {
    const logs = await this.prisma.operationLog.findMany({
      include: { user: { select: { login: true, role: true } } },
      orderBy: { createdAt: 'desc' },
      take: Math.min(Math.max(Number(limit) || 100, 1), 500),
    });
    return logs.map((log) => ({
      guid: log.id,
      action: log.action,
      route: log.route,
      entity_type: log.entityType,
      entity_id: log.entityId,
      details: log.details,
      ip_address: log.ipAddress,
      user_agent: log.userAgent,
      user: log.user,
      created_at: log.createdAt.toISOString(),
    }));
  }
}
