import {
  Body,
  Controller,
  Delete,
  Get,
  NotFoundException,
  Post,
  Query,
} from '@nestjs/common';
import { ApiBearerAuth, ApiQuery } from '@nestjs/swagger';
import { isUUID } from 'class-validator';
import {
  BoxDto,
  BoxOrderDto,
  GuidDto,
  PartialDto,
  SaveOrderDto,
  SendPartialDto,
} from './dto.js';
import { NotificationType, Prisma, Role } from './generated/prisma/client.js';
import { Roles } from './auth.js';
import { NotificationService } from './notification.service.js';
import { PrismaService } from './prisma.service.js';

@ApiBearerAuth()
@Roles(Role.ADMIN, Role.WORKER)
@Controller('api/order')
export class WarehouseController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly notifications: NotificationService,
  ) {}

  @Get('order-tracking-code')
  @ApiQuery({ name: 'tcode', required: false, type: String })
  @ApiQuery({ name: 'boxId', required: false, type: String })
  async orderDetails(
    @Query('tcode') tcode?: string,
    @Query('boxId') boxId?: string,
  ) {
    if (boxId) {
      const box = await this.findBox(boxId);
      if (!box) return [];
      const orders = await this.prisma.shipmentOrder.findMany({
        where: { boxId: box.id },
        include: { receiver: true, composition: true },
        orderBy: { createdAt: 'asc' },
      });
      return orders.map((order) => this.orderDetailsResponse(order));
    }
    if (!tcode) return [];
    const order = await this.prisma.shipmentOrder.findUnique({
      where: { trackingNumber: tcode },
      include: { receiver: true, composition: true },
    });
    return order ? [this.orderDetailsResponse(order)] : [];
  }

  @Get('order-search')
  async orderSearch(@Query('tcode') tcode: string) {
    if (!tcode?.trim()) return [];
    const order = await this.prisma.shipmentOrder.findUnique({
      where: { trackingNumber: tcode.trim() },
      include: { composition: true },
    });
    return order
      ? [
          {
            guid: order.id,
            tracking_number: order.trackingNumber,
            tracking_code: order.trackingNumber,
            status_name: order.statusName,
            gross_weight: this.number(order.grossWeight),
            client_code: order.clientCode,
            client: order.clientName,
            composition_guid: order.compositionId,
            composition: order.composition?.name ?? null,
            description: order.productDescription,
            declared_value: this.number(order.declaredValue),
            currency: order.currency,
          },
        ]
      : [];
  }

  @Post('set-user')
  async saveOrder(@Body() dto: SaveOrderDto) {
    const receiver = await this.prisma.receiver.findFirst({
      where: {
        OR: [
          { id: dto.reciever_guid || dto.receiver_guid },
          { clientCode: dto.client_code.trim().toUpperCase() },
        ],
      },
    });
    if (!receiver) throw new NotFoundException('Receiver not found');
    const composition = dto.composition_guid
      ? await this.prisma.orderComposition.findUnique({
          where: { id: dto.composition_guid },
        })
      : dto.composition_code
        ? await this.prisma.orderComposition.findUnique({
            where: { code: dto.composition_code.trim().toUpperCase() },
          })
        : null;
    if ((dto.composition_guid || dto.composition_code) && !composition) {
      throw new NotFoundException('Order composition not found');
    }
    const existing = await this.prisma.shipmentOrder.findUnique({
      where: { trackingNumber: dto.tracking_number.trim() },
    });
    const order = await this.prisma.shipmentOrder.upsert({
      where: { trackingNumber: dto.tracking_number.trim() },
      update: {
        clientName: dto.client?.trim(),
        clientCode: dto.client_code.trim().toUpperCase(),
        receiverId: receiver.id,
        withBox: this.flag(dto.with_box),
        grossWeight: dto.gross_weight,
        length: dto.length,
        width: dto.width,
        height: dto.height,
        statusName: 'AT_WAREHOUSE',
        ...(composition ? { compositionId: composition.id } : {}),
        ...(dto.description !== undefined
          ? { productDescription: dto.description.trim() || null }
          : {}),
        ...(dto.declared_value !== undefined
          ? { declaredValue: dto.declared_value }
          : {}),
        ...(dto.currency !== undefined
          ? { currency: dto.currency.trim().toUpperCase() }
          : {}),
      },
      create: {
        trackingNumber: dto.tracking_number.trim(),
        clientName: dto.client?.trim(),
        clientCode: dto.client_code.trim().toUpperCase(),
        receiverId: receiver.id,
        statusName: 'AT_WAREHOUSE',
        withBox: this.flag(dto.with_box),
        grossWeight: dto.gross_weight,
        length: dto.length,
        width: dto.width,
        height: dto.height,
        compositionId: composition?.id,
        productDescription: dto.description?.trim() || null,
        declaredValue: dto.declared_value,
        currency: dto.currency?.trim().toUpperCase() || 'EUR',
      },
    });
    if (!existing) {
      await this.notifications.createForReceiver(
        receiver.id,
        NotificationType.ORDER_REGISTERED,
        'Order registered',
        `Order ${order.trackingNumber} has been registered at the warehouse.`,
      );
    }
    return { success: true };
  }

  @Get('box')
  @ApiQuery({ name: 'guid', required: false, type: String })
  @ApiQuery({ name: 'tCode', required: false, type: String })
  @ApiQuery({ name: 'status', required: false, type: Number })
  async boxes(
    @Query('guid') guid = '',
    @Query('tCode') trackingCode = '',
    @Query('status') status?: string,
  ) {
    const where: Prisma.WarehouseBoxWhereInput = guid
      ? { id: guid }
      : trackingCode
        ? { trackingCode }
        : status !== undefined
          ? { status: Number(status) }
          : {};
    const boxes = await this.prisma.warehouseBox.findMany({
      where,
      include: { partial: true, _count: { select: { orders: true } } },
      orderBy: { createdAt: 'desc' },
    });
    return boxes.map((box) => ({
      guid: box.id,
      codeid: box.codeId,
      partial: box.partialId ?? '',
      partial_name: box.partial?.partialName ?? null,
      tracking_code: box.trackingCode,
      warehouse: box.warehouse,
      warehouse_name: box.partial?.warehouseName ?? box.warehouse,
      warehouse_guid: box.warehouseGuid,
      width: box.width,
      height: box.height,
      length: box.length,
      v_weight: this.number(box.volumeWeight) ?? 0,
      gross_weight: this.number(box.grossWeight) ?? 0,
      chargeable_weight: Math.max(
        this.number(box.grossWeight) ?? 0,
        this.number(box.volumeWeight) ?? 0,
      ),
      date_created: box.createdAt.toISOString(),
      orderCount: box._count.orders,
    }));
  }

  @Post('box')
  async saveBox(@Body() dto: BoxDto) {
    if (dto.partial) {
      if (!dto.guid) throw new NotFoundException('Box not found');
      const box = await this.prisma.warehouseBox.update({
        where: { id: dto.guid },
        data: { partialId: dto.partial },
      });
      return {
        success: true,
        message: 'Box added to batch',
        result: [{ UpdatedCodeID: box.codeId }],
      };
    }
    if (!dto.tracking_code?.trim())
      throw new NotFoundException('tracking_code is required');
    const existing = dto.guid
      ? await this.prisma.warehouseBox.findUnique({ where: { id: dto.guid } })
      : await this.prisma.warehouseBox.findUnique({
          where: { trackingCode: dto.tracking_code.trim() },
        });
    const box = existing
      ? await this.prisma.warehouseBox.update({
          where: { id: existing.id },
          data: {
            trackingCode: dto.tracking_code.trim(),
            width: dto.width,
            height: dto.height,
            length: dto.length,
            volumeWeight: dto.v_weight,
            grossWeight: dto.gross_weight,
          },
        })
      : await this.prisma.warehouseBox.create({
          data: {
            codeId:
              dto.codeid?.trim() ||
              String((await this.prisma.warehouseBox.count()) + 1),
            trackingCode: dto.tracking_code.trim(),
            width: dto.width ?? 0,
            height: dto.height ?? 0,
            length: dto.length ?? 0,
            volumeWeight: dto.v_weight ?? 0,
            grossWeight: dto.gross_weight,
            warehouse: (await this.platformSettings()).warehouseName,
          },
        });
    return {
      success: true,
      message: 'Box saved',
      result: [{ NewCodeID: Number(box.codeId) || null }],
    };
  }

  @Post('box-order')
  async addOrder(@Body() dto: BoxOrderDto) {
    const [order, box] = await Promise.all([
      this.prisma.shipmentOrder.findUnique({ where: { id: dto.order } }),
      this.prisma.warehouseBox.findUnique({ where: { id: dto.box } }),
    ]);
    if (!order || !box) throw new NotFoundException('Order or box not found');
    const settings = await this.platformSettings();
    const saved = await this.prisma.shipmentOrder.update({
      where: { id: order.id },
      data: {
        boxId: box.id,
        boxDescription: dto.desc?.trim() || settings.country,
      },
    });
    if (saved.receiverId) {
      await this.notifications.createForReceiver(
        saved.receiverId,
        NotificationType.ORDER_PACKED,
        'Order packed',
        `Order ${saved.trackingNumber} has been added to box ${box.trackingCode}.`,
      );
    }
    return [
      {
        success: true,
        message: 'Order added to box',
        error: '',
        guid: saved.id,
      },
    ];
  }

  @Delete('box-order')
  async removeOrder(@Body() dto: GuidDto) {
    const order = await this.prisma.shipmentOrder.update({
      where: { id: dto.guid },
      data: { boxId: null, boxDescription: null },
    });
    if (order.receiverId) {
      await this.notifications.createForReceiver(
        order.receiverId,
        NotificationType.ORDER_UNPACKED,
        'Order removed from box',
        `Order ${order.trackingNumber} is no longer assigned to a box.`,
      );
    }
    return { success: true, message: 'Order removed from box' };
  }

  @Get('partial')
  @ApiQuery({ name: 'typePartial', required: false, type: Number })
  async partials(@Query('typePartial') type?: string) {
    const batches = await this.prisma.partialBatch.findMany({
      where: type === undefined ? {} : { status: Number(type) },
      include: { boxes: true },
      orderBy: { createdAt: 'desc' },
    });
    return batches.map((batch) => ({
      guid: batch.id,
      codeid: batch.codeId,
      partial: batch.id,
      partial_code: batch.partialCode,
      partial_name: batch.partialName,
      warehouse_name: batch.warehouseName,
      date_created: batch.createdAt.toISOString(),
      boxCount: batch.boxes.length,
      status: batch.status,
      gross_weight:
        this.number(batch.grossWeight) ??
        batch.boxes.reduce(
          (sum, box) => sum + (this.number(box.grossWeight) ?? 0),
          0,
        ),
      volume_weight: batch.boxes.reduce(
        (sum, box) => sum + (this.number(box.volumeWeight) ?? 0),
        0,
      ),
      chargeable_weight: batch.boxes.reduce(
        (sum, box) =>
          sum + Math.max(
            this.number(box.grossWeight) ?? 0,
            this.number(box.volumeWeight) ?? 0,
          ),
        0,
      ),
    }));
  }

  @Post('partial')
  async savePartial(@Body() dto: PartialDto) {
    if (dto.guid) {
      await this.prisma.partialBatch.update({
        where: { id: dto.guid },
        data: {
          partialCode: dto.partial_code,
          partialName: dto.partial_name,
          status: dto.status,
        },
      });
    } else {
      await this.prisma.partialBatch.create({
        data: {
          codeId: String((await this.prisma.partialBatch.count()) + 1),
          partialCode: dto.partial_code,
          partialName: dto.partial_name,
          warehouseName: (await this.platformSettings()).warehouseName,
        },
      });
    }
    return { success: true };
  }

  @Post('send-partial')
  async sendPartial(@Body() dto: SendPartialDto) {
    await this.prisma.partialBatch.update({
      where: { id: dto.partial },
      data: { status: 1 },
    });
    return {
      success: true,
      message: 'Batch completed',
      result: {},
    };
  }

  @Get('partial-box')
  async partialBoxes(@Query('guid') guid: string) {
    const boxes = await this.prisma.warehouseBox.findMany({
      where: { partialId: guid },
      include: { partial: true },
      orderBy: { createdAt: 'asc' },
    });
    return boxes.map((box) => ({
      guid: box.id,
      codeid: box.codeId,
      partial: box.partialId ?? '',
      partial_name: box.partial?.partialName ?? null,
      tracking_code: box.trackingCode,
      width: box.width,
      height: box.height,
      length: box.length,
      v_weight: this.number(box.volumeWeight) ?? 0,
      gross_weight: this.number(box.grossWeight) ?? 0,
      chargeable_weight: Math.max(
        this.number(box.grossWeight) ?? 0,
        this.number(box.volumeWeight) ?? 0,
      ),
      date_created: box.createdAt.toISOString(),
      warehouse_name: box.partial?.warehouseName ?? box.warehouse,
    }));
  }

  @Delete('partial-box')
  async removePartialBox(@Body() body: Record<string, string>) {
    const value = body.guid || body.box || body.tracking_code;
    const box = value ? await this.findBox(value) : null;
    if (!box) throw new NotFoundException('Box not found');
    await this.prisma.warehouseBox.update({
      where: { id: box.id },
      data: { partialId: null },
    });
    return { success: true, message: 'Box removed from batch' };
  }

  private async platformSettings() {
    const config = await this.prisma.clientCodeConfig.findUnique({
      where: { id: 1 },
    });
    return {
      warehouseName: config?.warehouseName ?? 'BMG',
      country: config?.country ?? 'España',
    };
  }

  private findBox(value: string) {
    const normalized = value.trim();
    return this.prisma.warehouseBox.findFirst({
      where: isUUID(normalized)
        ? { OR: [{ id: normalized }, { trackingCode: normalized }] }
        : { trackingCode: normalized },
    });
  }

  private orderDetailsResponse(order: {
    id: string;
    trackingNumber: string;
    clientName: string | null;
    clientCode: string | null;
    withBox: number;
    grossWeight: Prisma.Decimal | null;
    compositionId: string | null;
    productDescription: string | null;
    declaredValue: Prisma.Decimal | null;
    currency: string;
    composition: { name: string } | null;
    receiverId: string | null;
    length: number | null;
    width: number | null;
    height: number | null;
  }) {
    return {
      guid: order.id,
      tracking_number: order.trackingNumber,
      client: order.clientName,
      client_code: order.clientCode,
      with_box: order.withBox ? String(order.withBox) : null,
      gross_weight: this.number(order.grossWeight),
      composition_guid: order.compositionId,
      composition: order.composition?.name ?? null,
      description: order.productDescription,
      declared_value: this.number(order.declaredValue),
      currency: order.currency,
      user_guid: order.receiverId,
      length: order.length,
      width: order.width,
      height: order.height,
    };
  }

  private number(value: Prisma.Decimal | null | undefined) {
    return value == null ? null : Number(value.toString());
  }

  private flag(value: unknown) {
    if (typeof value === 'boolean') return value ? 1 : 0;
    if (typeof value === 'number') return value;
    if (typeof value === 'string')
      return value === '1' || value.toLowerCase() === 'true' ? 1 : 0;
    return 0;
  }
}
