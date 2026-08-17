import {
  BadRequestException,
  Body,
  ConflictException,
  Controller,
  Get,
  NotFoundException,
  Param,
  Post,
  Put,
  Query,
} from '@nestjs/common';
import { ApiBearerAuth } from '@nestjs/swagger';
import { hash } from 'bcryptjs';
import {
  DictionaryItemDto,
  NotificationDto,
  OrderCompositionDto,
  PlatformSettingsDto,
  StaffUserDto,
  StaffUserStatusDto,
} from './dto.js';
import {
  DictionaryType,
  NotificationType,
  Role,
} from './generated/prisma/client.js';
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

  @Put('users/:id/enabled')
  async setUserEnabled(
    @Param('id') id: string,
    @Body() dto: StaffUserStatusDto,
  ) {
    const user = await this.prisma.user.findUnique({ where: { id } });
    if (!user || user.role === Role.CLIENT) {
      throw new NotFoundException('Staff user not found');
    }
    if (user.role === Role.ADMIN && !dto.enabled) {
      throw new BadRequestException('Admin accounts cannot be blocked here');
    }
    const updated = await this.prisma.user.update({
      where: { id },
      data: { enabled: dto.enabled },
      select: {
        id: true,
        login: true,
        role: true,
        enabled: true,
        createdAt: true,
      },
    });
    return updated;
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

  @Get('order-compositions')
  async orderCompositions() {
    const items = await this.prisma.orderComposition.findMany({
      include: { _count: { select: { orders: true } } },
      orderBy: [{ sortOrder: 'asc' }, { name: 'asc' }],
    });
    return items.map((item) => ({
      guid: item.id,
      code: item.code,
      name: item.name,
      enabled: item.enabled,
      sort_order: item.sortOrder,
      used_count: item._count.orders,
    }));
  }

  @Post('order-compositions')
  async createOrderComposition(@Body() dto: OrderCompositionDto) {
    const code = dto.code.trim().toUpperCase();
    if (await this.prisma.orderComposition.findUnique({ where: { code } })) {
      throw new ConflictException('Order composition already exists');
    }
    const item = await this.prisma.orderComposition.create({
      data: {
        code,
        name: dto.name.trim(),
        sortOrder: dto.sort_order,
      },
      include: { _count: { select: { orders: true } } },
    });
    return this.orderCompositionResponse(item);
  }

  @Put('order-compositions/:id')
  async updateOrderComposition(
    @Param('id') id: string,
    @Body() dto: OrderCompositionDto,
  ) {
    const code = dto.code.trim().toUpperCase();
    const existing = await this.prisma.orderComposition.findUnique({
      where: { code },
    });
    if (existing && existing.id !== id) {
      throw new ConflictException('Order composition already exists');
    }
    const item = await this.prisma.orderComposition.update({
      where: { id },
      data: {
        code,
        name: dto.name.trim(),
        sortOrder: dto.sort_order,
      },
      include: { _count: { select: { orders: true } } },
    });
    return this.orderCompositionResponse(item);
  }

  @Put('order-compositions/:id/enabled')
  async setOrderCompositionEnabled(
    @Param('id') id: string,
    @Body() dto: StaffUserStatusDto,
  ) {
    const item = await this.prisma.orderComposition.update({
      where: { id },
      data: { enabled: dto.enabled },
      include: { _count: { select: { orders: true } } },
    });
    return this.orderCompositionResponse(item);
  }

  @Get('dictionaries')
  async dictionaries(@Query('type') type?: DictionaryType) {
    const items = await this.prisma.dictionaryItem.findMany({
      where: type ? { type } : {},
      orderBy: [{ type: 'asc' }, { sortOrder: 'asc' }, { name: 'asc' }],
    });
    return Promise.all(items.map((item) => this.dictionaryResponse(item)));
  }

  @Post('dictionaries')
  async createDictionaryItem(@Body() dto: DictionaryItemDto) {
    const item = await this.prisma.dictionaryItem.create({
      data: this.dictionaryData(dto),
    });
    return this.dictionaryResponse(item);
  }

  @Put('dictionaries/:id')
  async updateDictionaryItem(
    @Param('id') id: string,
    @Body() dto: DictionaryItemDto,
  ) {
    const item = await this.prisma.dictionaryItem.update({
      where: { id },
      data: this.dictionaryData(dto),
    });
    return this.dictionaryResponse(item);
  }

  @Put('dictionaries/:id/enabled')
  async setDictionaryItemEnabled(
    @Param('id') id: string,
    @Body() dto: StaffUserStatusDto,
  ) {
    const item = await this.prisma.dictionaryItem.update({
      where: { id },
      data: { enabled: dto.enabled },
    });
    return this.dictionaryResponse(item);
  }

  @Put('client-code-settings')
  async updateCodeSettings(@Body() dto: PlatformSettingsDto) {
    const prefix = dto.prefix.trim().toUpperCase();
    const warehouse = dto.warehouse.trim();
    const country = dto.country.trim();
    if (!/^[A-Z0-9-]{1,12}$/.test(prefix)) {
      throw new BadRequestException(
        'Prefix must contain only A-Z, 0-9 or hyphen',
      );
    }
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

  private orderCompositionResponse(item: {
    id: string;
    code: string;
    name: string;
    enabled: boolean;
    sortOrder: number;
    _count: { orders: number };
  }) {
    return {
      guid: item.id,
      code: item.code,
      name: item.name,
      enabled: item.enabled,
      sort_order: item.sortOrder,
      used_count: item._count.orders,
    };
  }

  private dictionaryData(dto: DictionaryItemDto) {
    return {
      type: dto.type,
      code: dto.code.trim().toUpperCase(),
      name: dto.name.trim(),
      sortOrder: dto.sort_order,
    };
  }

  private async dictionaryResponse(item: {
    id: string;
    type: DictionaryType;
    code: string;
    name: string;
    enabled: boolean;
    sortOrder: number;
  }) {
    return {
      guid: item.id,
      type: item.type,
      code: item.code,
      name: item.name,
      enabled: item.enabled,
      sort_order: item.sortOrder,
      used_count: await this.dictionaryUsage(item.type, item.code, item.name),
    };
  }

  private async dictionaryUsage(
    type: DictionaryType,
    code: string,
    name: string,
  ) {
    if (type === DictionaryType.CURRENCY) {
      return this.prisma.shipmentOrder.count({ where: { currency: code } });
    }
    if (type === DictionaryType.ORDER_STATUS) {
      return this.prisma.shipmentOrder.count({
        where: { OR: [{ statusName: code }, { statusName: name }] },
      });
    }
    if (type === DictionaryType.WAREHOUSE) {
      const [boxes, batches, manifests] = await Promise.all([
        this.prisma.warehouseBox.count({
          where: { OR: [{ warehouse: code }, { warehouse: name }] },
        }),
        this.prisma.partialBatch.count({
          where: { OR: [{ warehouseName: code }, { warehouseName: name }] },
        }),
        this.prisma.manifest.count({
          where: { OR: [{ warehouseName: code }, { warehouseName: name }] },
        }),
      ]);
      return boxes + batches + manifests;
    }
    if (type === DictionaryType.COUNTRY) {
      const [settings, manifests] = await Promise.all([
        this.prisma.clientCodeConfig.count({
          where: { OR: [{ country: code }, { country: name }] },
        }),
        this.prisma.manifest.count({
          where: { OR: [{ country: code }, { country: name }] },
        }),
      ]);
      return settings + manifests;
    }
    return 0;
  }
}
