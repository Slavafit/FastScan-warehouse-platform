import {
  BadRequestException,
  Body,
  Controller,
  Get,
  NotFoundException,
  Param,
  Post,
  Put,
  Req,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { AuthUser, Roles } from './auth.js';
import { ManifestCreateDto, StaffClientUpdateDto } from './dto.js';
import { Prisma, Role } from './generated/prisma/client.js';
import { PrismaService } from './prisma.service.js';

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
        account: true,
        _count: { select: { orders: true } },
      },
      orderBy: [{ lastName: 'asc' }, { firstName: 'asc' }],
    });
    return clients.map((client) => this.clientResponse(client));
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
        account: true,
        _count: { select: { orders: true } },
      },
    });
    if (!updated) throw new NotFoundException('Client not found');
    return this.clientResponse(updated);
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
        items: {
          create: orders.map(({ box, order }) => ({
            sourceOrderId: order.id,
            trackingNumber: order.trackingNumber,
            clientCode: order.clientCode ?? order.receiver?.clientCode,
            clientName:
              order.clientName ??
              (order.receiver
                ? `${order.receiver.firstName} ${order.receiver.lastName}`.trim()
                : null),
            clientPhone: order.receiver?.account?.phone,
            clientAddress: order.receiver?.account?.address,
            grossWeight: order.grossWeight,
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
      package_shoes: client.packageShoes,
      bag_package: client.bagPackage,
      orders_count: client._count.orders,
    };
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
      created_by: manifest.createdBy,
      created_at: manifest.createdAt.toISOString(),
      orders: manifest.items.map((item) => ({
        guid: item.sourceOrderId,
        tracking_number: item.trackingNumber,
        client_code: item.clientCode,
        client_name: item.clientName,
        client_phone: item.clientPhone,
        client_address: item.clientAddress,
        gross_weight: this.number(item.grossWeight),
        box_tracking_code: item.boxTrackingCode,
      })),
    };
  }

  private optionalText(value: string) {
    const normalized = value.trim();
    return normalized || null;
  }

  private number(value: Prisma.Decimal | null) {
    return value == null ? 0 : Number(value.toString());
  }
}
