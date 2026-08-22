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
  Req,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { hash } from 'bcryptjs';
import { randomInt } from 'node:crypto';
import { AuthUser, Roles } from './auth.js';
import {
  ManifestCreateDto,
  PasswordResetDto,
  StaffClientCreateDto,
  StaffClientUpdateDto,
  StaffUserStatusDto,
} from './dto.js';
import { Prisma, Role } from './generated/prisma/client.js';
import { PrismaService } from './prisma.service.js';
import { calculateWeightBreakdown } from './weight.util.js';

@ApiTags('Staff')
@ApiBearerAuth()
@Roles(Role.ADMIN, Role.WORKER)
@Controller('api/staff')
export class StaffController {
  constructor(private readonly prisma: PrismaService) {}

  @Get('clients')
  async clients() {
    const clients = await this.prisma.receiver.findMany({
      include: {
        account: { include: { user: true } },
        _count: { select: { orders: true } },
      },
      orderBy: [{ lastName: 'asc' }, { firstName: 'asc' }],
    });
    return clients.map((client) => this.clientResponse(client));
  }

  @Post('clients')
  async createClient(@Body() dto: StaffClientCreateDto) {
    const clientCode =
      dto.client_code?.trim().toUpperCase() || (await this.randomClientCode());
    const email = dto.email?.trim().toLowerCase();
    if (await this.prisma.receiver.findUnique({ where: { clientCode } })) {
      throw new ConflictException('Client code already exists');
    }
    if (email) {
      if (!dto.password) {
        throw new BadRequestException(
          'Password is required for client account',
        );
      }
      const existing = await this.prisma.user.findUnique({
        where: { login: email },
      });
      if (existing) throw new ConflictException('Client email already exists');
    }

    const receiver = await this.prisma.receiver.create({
      data: {
        clientCode,
        firstName: dto.first_name.trim(),
        lastName: dto.last_name.trim(),
        packageShoes: dto.package_shoes,
        bagPackage: dto.bag_package,
        ...(email
          ? {
              account: {
                create: {
                  email,
                  phone: this.optionalText(dto.phone),
                  address: this.optionalText(dto.address),
                  user: {
                    create: {
                      login: email,
                      passwordHash: await hash(dto.password!, 12),
                      role: Role.CLIENT,
                    },
                  },
                },
              },
            }
          : {}),
      },
      include: {
        account: { include: { user: true } },
        _count: { select: { orders: true } },
      },
    });
    return this.clientResponse(receiver);
  }

  @Put('clients/:guid')
  async updateClient(
    @Param('guid') guid: string,
    @Body() dto: StaffClientUpdateDto,
  ) {
    const receiver = await this.prisma.receiver.findUnique({
      where: { id: guid },
      include: { account: true },
    });
    if (!receiver) throw new NotFoundException('Client not found');

    await this.prisma.$transaction(async (transaction) => {
      await transaction.receiver.update({
        where: { id: receiver.id },
        data: {
          ...(dto.first_name !== undefined
            ? { firstName: dto.first_name.trim() }
            : {}),
          ...(dto.last_name !== undefined
            ? { lastName: dto.last_name.trim() }
            : {}),
          ...(dto.package_shoes !== undefined
            ? { packageShoes: dto.package_shoes }
            : {}),
          ...(dto.bag_package !== undefined
            ? { bagPackage: dto.bag_package }
            : {}),
        },
      });

      if (receiver.account) {
        const email = dto.email?.trim().toLowerCase();
        if (email && email !== receiver.account.email) {
          await transaction.user.update({
            where: { id: receiver.account.userId },
            data: { login: email },
          });
        }
        await transaction.clientAccount.update({
          where: { id: receiver.account.id },
          data: {
            ...(email ? { email } : {}),
            ...(dto.phone !== undefined
              ? { phone: this.optionalText(dto.phone) }
              : {}),
            ...(dto.address !== undefined
              ? { address: this.optionalText(dto.address) }
              : {}),
          },
        });
      }
    });

    const updated = await this.prisma.receiver.findUnique({
      where: { id: receiver.id },
      include: {
        account: { include: { user: true } },
        _count: { select: { orders: true } },
      },
    });
    if (!updated) throw new NotFoundException('Client not found');
    return this.clientResponse(updated);
  }

  @Put('clients/:guid/enabled')
  async setClientEnabled(
    @Param('guid') guid: string,
    @Body() dto: StaffUserStatusDto,
  ) {
    const receiver = await this.prisma.receiver.findUnique({
      where: { id: guid },
      include: { account: true },
    });
    if (!receiver) throw new NotFoundException('Client not found');
    if (!receiver.account)
      throw new BadRequestException('Client has no account');
    await this.prisma.user.update({
      where: { id: receiver.account.userId },
      data: { enabled: dto.enabled },
    });
    return this.clientById(receiver.id);
  }

  @Post('clients/:guid/reset-password')
  async resetClientPassword(
    @Param('guid') guid: string,
    @Body() dto: PasswordResetDto,
  ) {
    const receiver = await this.prisma.receiver.findUnique({
      where: { id: guid },
      include: { account: true },
    });
    if (!receiver) throw new NotFoundException('Client not found');
    if (!receiver.account)
      throw new BadRequestException('Client has no account');
    await this.prisma.user.update({
      where: { id: receiver.account.userId },
      data: { passwordHash: await hash(dto.password, 12) },
    });
    return { success: true };
  }

  @Get('clients/:guid/orders')
  async clientOrders(@Param('guid') guid: string) {
    const receiver = await this.prisma.receiver.findUnique({
      where: { id: guid },
    });
    if (!receiver) throw new NotFoundException('Client not found');
    const orders = await this.prisma.shipmentOrder.findMany({
      where: { receiverId: receiver.id },
      include: { box: true, composition: true },
      orderBy: { createdAt: 'desc' },
    });
    const divisor = await this.volumetricDivisor();
    return orders.map((order) => ({
      guid: order.id,
      tracking_number: order.trackingNumber,
      status_name: order.statusName,
      ...calculateWeightBreakdown(
        this.number(order.grossWeight),
        order.length,
        order.width,
        order.height,
        divisor,
      ),
      with_box: order.withBox !== 0,
      box_tracking_code: order.box?.trackingCode ?? null,
      composition_guid: order.compositionId,
      composition: order.composition?.name ?? null,
      description: order.productDescription,
      declared_value: this.number(order.declaredValue),
      currency: order.currency,
      created_at: order.createdAt.toISOString(),
      updated_at: order.updatedAt.toISOString(),
    }));
  }

  @Get('clients/:guid/analytics')
  async clientAnalytics(@Param('guid') guid: string) {
    const receiver = await this.prisma.receiver.findUnique({
      where: { id: guid },
    });
    if (!receiver) throw new NotFoundException('Client not found');
    const orders = await this.prisma.shipmentOrder.findMany({
      where: { receiverId: receiver.id },
      select: { grossWeight: true, boxId: true, statusName: true },
    });
    const statuses: Record<string, number> = {};
    for (const order of orders) {
      statuses[order.statusName] = (statuses[order.statusName] ?? 0) + 1;
    }
    const packed = orders.filter((order) => order.boxId).length;
    return {
      total_orders: orders.length,
      packed_orders: packed,
      unpacked_orders: orders.length - packed,
      total_weight: orders.reduce(
        (sum, order) => sum + this.number(order.grossWeight),
        0,
      ),
      boxes_count: new Set(orders.map((order) => order.boxId).filter(Boolean))
        .size,
      status_breakdown: statuses,
      unread_notifications: 0,
    };
  }

  @Get('manifests')
  async manifests() {
    const manifests = await this.prisma.manifest.findMany({
      include: {
        items: true,
        partialBatch: true,
        createdBy: { select: { login: true, role: true } },
      },
      orderBy: { createdAt: 'desc' },
    });
    return manifests.map((manifest) => this.manifestResponse(manifest));
  }

  @Get('manifests/:guid')
  async manifest(@Param('guid') guid: string) {
    const manifest = await this.findManifest(guid);
    if (!manifest) throw new NotFoundException('Manifest not found');
    return this.manifestResponse(manifest);
  }

  @Post('manifests')
  async createManifest(
    @Req() request: { user: AuthUser },
    @Body() dto: ManifestCreateDto,
  ) {
    const existing = await this.prisma.manifest.findUnique({
      where: { partialBatchId: dto.partial_guid },
      include: {
        items: true,
        partialBatch: true,
        createdBy: { select: { login: true, role: true } },
      },
    });
    if (existing) {
      return {
        success: true,
        created: false,
        manifest: this.manifestResponse(existing),
      };
    }

    const batch = await this.prisma.partialBatch.findUnique({
      where: { id: dto.partial_guid },
      include: {
        boxes: {
          include: {
            orders: {
              include: {
                receiver: { include: { account: true } },
                composition: true,
              },
            },
          },
        },
      },
    });
    if (!batch) throw new NotFoundException('Batch not found');
    if (batch.status !== 1) {
      throw new BadRequestException(
        'Manifest can only be created for a completed batch',
      );
    }

    const orders = batch.boxes.flatMap((box) =>
      box.orders.map((order) => ({ box, order })),
    );
    if (orders.length === 0) {
      throw new BadRequestException('Batch does not contain orders');
    }
    const settings = await this.prisma.clientCodeConfig.findUnique({
      where: { id: 1 },
    });
    const grossWeight = orders.reduce(
      (sum, { order }) => sum + this.number(order.grossWeight),
      0,
    );
    const divisor = settings?.volumetricDivisor ?? 6000;
    const orderWeights = orders.map(({ order }) =>
      calculateWeightBreakdown(
        this.number(order.grossWeight),
        order.length,
        order.width,
        order.height,
        divisor,
      ),
    );
    const volumeWeight = orderWeights.reduce(
      (sum, weight) => sum + (weight.volume_weight ?? 0),
      0,
    );
    const chargeableWeight = orderWeights.reduce(
      (sum, weight) => sum + (weight.chargeable_weight ?? 0),
      0,
    );
    const manifest = await this.prisma.manifest.create({
      data: {
        manifestCode: `MF-${batch.partialCode}`,
        partialBatchId: batch.id,
        createdByUserId: request.user.sub,
        warehouseName: batch.warehouseName,
        country: settings?.country ?? 'España',
        batchCode: batch.partialCode,
        batchName: batch.partialName,
        boxCount: batch.boxes.length,
        orderCount: orders.length,
        grossWeight,
        volumeWeight,
        chargeableWeight,
        items: {
          create: orders.map(({ box, order }, index) => ({
            sourceOrderId: order.id,
            trackingNumber: order.trackingNumber,
            clientCode: order.clientCode ?? order.receiver?.clientCode,
            clientName:
              order.clientName ??
              (order.receiver
                ? `${order.receiver.firstName} ${order.receiver.lastName}`.trim()
                : null),
            clientFirstName: order.receiver?.firstName,
            clientLastName: order.receiver?.lastName,
            clientEmail: order.receiver?.account?.email,
            clientPhone: order.receiver?.account?.phone,
            clientAddress: order.receiver?.account?.address,
            compositionName: order.composition?.name,
            productDescription: order.productDescription,
            declaredValue: order.declaredValue,
            currency: order.currency,
            grossWeight: order.grossWeight,
            volumeWeight: orderWeights[index].volume_weight,
            chargeableWeight: orderWeights[index].chargeable_weight,
            boxTrackingCode: box.trackingCode,
          })),
        },
      },
      include: {
        items: true,
        partialBatch: true,
        createdBy: { select: { login: true, role: true } },
      },
    });
    return {
      success: true,
      created: true,
      manifest: this.manifestResponse(manifest),
    };
  }

  private findManifest(guid: string) {
    return this.prisma.manifest.findUnique({
      where: { id: guid },
      include: {
        items: {
          orderBy: [{ boxTrackingCode: 'asc' }, { trackingNumber: 'asc' }],
        },
        partialBatch: true,
        createdBy: { select: { login: true, role: true } },
      },
    });
  }

  private clientResponse(client: {
    id: string;
    clientCode: string;
    firstName: string;
    lastName: string;
    packageShoes: number;
    bagPackage: number;
    account: {
      email: string;
      phone: string | null;
      address: string | null;
      user?: { enabled: boolean };
    } | null;
    _count: { orders: number };
  }) {
    return {
      guid: client.id,
      client_code: client.clientCode,
      first_name: client.firstName,
      last_name: client.lastName,
      full_name: `${client.firstName} ${client.lastName}`.trim(),
      email: client.account?.email ?? null,
      phone: client.account?.phone ?? null,
      address: client.account?.address ?? null,
      has_account: client.account !== null,
      account_enabled: client.account?.user?.enabled ?? false,
      package_shoes: client.packageShoes,
      bag_package: client.bagPackage,
      orders_count: client._count.orders,
    };
  }

  private async clientById(id: string) {
    const client = await this.prisma.receiver.findUnique({
      where: { id },
      include: {
        account: { include: { user: true } },
        _count: { select: { orders: true } },
      },
    });
    if (!client) throw new NotFoundException('Client not found');
    return this.clientResponse(client);
  }

  private async randomClientCode() {
    const config = await this.prisma.clientCodeConfig.findUnique({
      where: { id: 1 },
    });
    const prefix = config?.codePrefix ?? 'KG';
    for (let attempt = 0; attempt < 100; attempt += 1) {
      const code = `${prefix}${randomInt(100_000, 1_000_000)}`;
      if (
        !(await this.prisma.receiver.findUnique({
          where: { clientCode: code },
        }))
      ) {
        return code;
      }
    }
    throw new ServiceUnavailableException(
      'Не удалось создать уникальный номер клиента. Повторите попытку',
    );
  }

  private manifestResponse(
    manifest: NonNullable<Awaited<ReturnType<StaffController['findManifest']>>>,
  ) {
    return {
      guid: manifest.id,
      manifest_code: manifest.manifestCode,
      partial_guid: manifest.partialBatchId,
      partial_code: manifest.batchCode,
      partial_name: manifest.batchName,
      warehouse_name: manifest.warehouseName,
      country: manifest.country,
      box_count: manifest.boxCount,
      order_count: manifest.orderCount,
      gross_weight: this.number(manifest.grossWeight),
      volume_weight: this.number(manifest.volumeWeight),
      chargeable_weight: this.number(manifest.chargeableWeight),
      created_by: manifest.createdBy,
      created_at: manifest.createdAt.toISOString(),
      orders: manifest.items.map((item) => ({
        guid: item.sourceOrderId,
        tracking_number: item.trackingNumber,
        client_code: item.clientCode,
        client_name: item.clientName,
        client_first_name: item.clientFirstName,
        client_last_name: item.clientLastName,
        client_email: item.clientEmail,
        client_phone: item.clientPhone,
        client_address: item.clientAddress,
        composition: item.compositionName,
        description: item.productDescription,
        declared_value: this.number(item.declaredValue),
        currency: item.currency,
        gross_weight: this.number(item.grossWeight),
        volume_weight: this.number(item.volumeWeight),
        chargeable_weight: this.number(item.chargeableWeight),
        box_tracking_code: item.boxTrackingCode,
      })),
    };
  }

  private optionalText(value?: string) {
    const normalized = value?.trim();
    return normalized || null;
  }

  private async volumetricDivisor() {
    const config = await this.prisma.clientCodeConfig?.findUnique({
      where: { id: 1 },
    });
    return config?.volumetricDivisor ?? 6000;
  }

  private number(value: Prisma.Decimal | null) {
    return value == null ? 0 : Number(value.toString());
  }
}
