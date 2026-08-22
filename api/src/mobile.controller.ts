import {
  Body,
  Controller,
  Delete,
  Get,
  NotFoundException,
  Param,
  Post,
  Put,
  Query,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ApiBearerAuth, ApiQuery } from '@nestjs/swagger';
import { isUUID } from 'class-validator';
import { randomInt } from 'node:crypto';
import {
  BoxDto,
  BoxOrderDto,
  MobileBatchBoxDto,
  MobileBoxOrderDto,
  PartialDto,
  ReceiverUpsertDto,
  SaveOrderDto,
} from './dto.js';
import { NotificationType, Prisma, Role } from './generated/prisma/client.js';
import { Roles } from './auth.js';
import { NotificationService } from './notification.service.js';
import { PrismaService } from './prisma.service.js';

@ApiBearerAuth()
@Roles(Role.ADMIN, Role.WORKER)
@Controller('api/mobile')
export class MobileController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly notifications: NotificationService,
  ) {}

  @Get('receivers')
  @ApiQuery({ name: 'client_code', required: false, type: String })
  async receivers(@Query('client_code') clientCode?: string) {
    if (clientCode) {
      const receiver = await this.prisma.receiver.findUnique({
        where: { clientCode: clientCode.trim().toUpperCase() },
      });
      return receiver ? [this.receiverResponse(receiver)] : [];
    }
    const result = await this.prisma.receiver.findMany({
      orderBy: [{ lastName: 'asc' }, { firstName: 'asc' }],
    });
    return result.map((item) => ({
      guid: item.id,
      client_code: item.clientCode,
      first_name: item.firstName,
      last_name: item.lastName,
      full_name: `${item.firstName} ${item.lastName}`.trim(),
      package_shoes: item.packageShoes,
      bag_package: item.bagPackage,
    }));
  }

  @Post('receivers')
  async saveReceiver(@Body() dto: ReceiverUpsertDto) {
    const explicit = dto.client_code?.trim().toUpperCase();
    const clientCode = explicit || (await this.randomClientCode());
    const receiver = await this.prisma.receiver.upsert({
      where: { clientCode },
      update: {
        firstName: dto.first_name.trim(),
        lastName: dto.last_name.trim(),
        packageShoes: dto.package_shoes,
        bagPackage: dto.bag_package,
      },
      create: {
        clientCode,
        firstName: dto.first_name.trim(),
        lastName: dto.last_name.trim(),
        packageShoes: dto.package_shoes,
        bagPackage: dto.bag_package,
      },
    });
    return {
      ...this.receiverResponse(receiver),
      client_code: receiver.clientCode,
    };
  }

  @Get('orders/:trackingNumber')
  async orderByTracking(@Param('trackingNumber') trackingNumber: string) {
    const order = await this.prisma.shipmentOrder.findUnique({
      where: { trackingNumber: trackingNumber.trim() },
      include: { receiver: true, composition: true, box: true },
    });
    return order ? this.orderResponse(order) : null;
  }

  @Get('orders')
  @ApiQuery({ name: 'tracking_number', required: false, type: String })
  @ApiQuery({ name: 'box_tracking_code', required: false, type: String })
  @ApiQuery({ name: 'unpacked', required: false, type: Boolean })
  @ApiQuery({ name: 'days', required: false, type: Number })
  async orders(
    @Query('tracking_number') trackingNumber?: string,
    @Query('box_tracking_code') boxTrackingCode?: string,
    @Query('unpacked') unpacked?: string,
    @Query('days') days?: string,
  ) {
    if (boxTrackingCode) {
      const box = await this.findBox(boxTrackingCode);
      if (!box) return [];
      const orders = await this.prisma.shipmentOrder.findMany({
        where: { boxId: box.id },
        include: { receiver: true, composition: true, box: true },
        orderBy: { createdAt: 'asc' },
      });
      return orders.map((order) => this.orderResponse(order));
    }
    if (unpacked === 'true') {
      const dayCount = Math.max(0, Number(days) || 0);
      const createdAt = dayCount
        ? { gte: new Date(Date.now() - dayCount * 24 * 60 * 60 * 1000) }
        : undefined;
      const orders = await this.prisma.shipmentOrder.findMany({
        where: { boxId: null, ...(createdAt ? { createdAt } : {}) },
        include: { receiver: true, composition: true, box: true },
        orderBy: { createdAt: 'desc' },
      });
      return orders.map((order) => this.orderResponse(order));
    }
    if (!trackingNumber) return [];
    const order = await this.prisma.shipmentOrder.findUnique({
      where: { trackingNumber: trackingNumber.trim() },
      include: { receiver: true, composition: true, box: true },
    });
    return order ? [this.orderResponse(order)] : [];
  }

  @Post('orders')
  async saveOrder(@Body() dto: SaveOrderDto) {
    const receiver = await this.prisma.receiver.findFirst({
      where: {
        OR: [
          { id: dto.receiver_guid || dto.reciever_guid },
          { clientCode: dto.client_code.trim().toUpperCase() },
        ],
      },
    });
    if (!receiver) throw new NotFoundException('Receiver not found');
    const composition = await this.findComposition(dto);
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
    return { success: true, guid: order.id };
  }

  @Get('boxes')
  @ApiQuery({ name: 'guid', required: false, type: String })
  @ApiQuery({ name: 'tracking_code', required: false, type: String })
  @ApiQuery({ name: 'status', required: false, type: Number })
  async boxes(
    @Query('guid') guid = '',
    @Query('tracking_code') trackingCode = '',
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
    return boxes.map((box) => this.boxResponse(box));
  }

  @Post('boxes')
  async createBox(@Body() dto: BoxDto) {
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
      message: existing ? 'Box updated' : 'Box created',
      guid: box.id,
      codeid: box.codeId,
      result: [{ NewCodeID: Number(box.codeId) || null }],
    };
  }

  @Put('boxes/:guid')
  async updateBox(@Param('guid') guid: string, @Body() dto: BoxDto) {
    const box = await this.prisma.warehouseBox.update({
      where: { id: guid },
      data: {
        ...(dto.tracking_code !== undefined
          ? { trackingCode: dto.tracking_code.trim() }
          : {}),
        ...(dto.width !== undefined ? { width: dto.width } : {}),
        ...(dto.height !== undefined ? { height: dto.height } : {}),
        ...(dto.length !== undefined ? { length: dto.length } : {}),
        ...(dto.v_weight !== undefined ? { volumeWeight: dto.v_weight } : {}),
        ...(dto.gross_weight !== undefined
          ? { grossWeight: dto.gross_weight }
          : {}),
      },
    });
    return { success: true, guid: box.id };
  }

  @Get('boxes/:guid/orders')
  async boxOrders(@Param('guid') guid: string) {
    const orders = await this.prisma.shipmentOrder.findMany({
      where: { boxId: guid },
      include: { receiver: true, composition: true, box: true },
      orderBy: { createdAt: 'asc' },
    });
    return orders.map((order) => this.orderResponse(order));
  }

  @Post('boxes/:guid/orders')
  async addOrderToBox(
    @Param('guid') guid: string,
    @Body() dto: MobileBoxOrderDto,
  ) {
    return this.assignOrderToBox({
      order: dto.order_guid,
      box: guid,
      desc: dto.description,
    });
  }

  @Post('boxes/orders')
  async addOrderToBoxByBody(@Body() dto: BoxOrderDto) {
    return [await this.assignOrderToBox(dto)];
  }

  @Delete('boxes/:guid/orders/:orderGuid')
  async removeOrderFromBox(@Param('orderGuid') orderGuid: string) {
    return this.unassignOrderFromBox(orderGuid);
  }

  @Delete('boxes/orders')
  async removeOrderFromBoxByBody(@Body() dto: { guid: string }) {
    return this.unassignOrderFromBox(dto.guid);
  }

  private async unassignOrderFromBox(orderGuid: string) {
    const order = await this.prisma.shipmentOrder.update({
      where: { id: orderGuid },
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

  @Get('batches')
  @ApiQuery({ name: 'status', required: false, type: Number })
  async batches(@Query('status') status?: string) {
    const batches = await this.prisma.partialBatch.findMany({
      where: status === undefined ? {} : { status: Number(status) },
      include: { boxes: true },
      orderBy: { createdAt: 'desc' },
    });
    return batches.map((batch) => this.batchResponse(batch));
  }

  @Post('batches')
  async createBatch(@Body() dto: PartialDto) {
    const batch = dto.guid
      ? await this.prisma.partialBatch.update({
          where: { id: dto.guid },
          data: {
            partialCode: dto.partial_code,
            partialName: dto.partial_name,
            status: dto.status,
          },
        })
      : await this.prisma.partialBatch.create({
          data: {
            codeId: String((await this.prisma.partialBatch.count()) + 1),
            partialCode: dto.partial_code,
            partialName: dto.partial_name,
            warehouseName: (await this.platformSettings()).warehouseName,
          },
        });
    return { success: true, guid: batch.id, codeid: batch.codeId };
  }

  @Put('batches/:guid')
  async updateBatch(@Param('guid') guid: string, @Body() dto: PartialDto) {
    const batch = await this.prisma.partialBatch.update({
      where: { id: guid },
      data: {
        partialCode: dto.partial_code,
        partialName: dto.partial_name,
        status: dto.status,
      },
    });
    return { success: true, guid: batch.id };
  }

  @Post('batches/:guid/complete')
  async completeBatch(@Param('guid') guid: string) {
    return this.markBatchCompleted(guid);
  }

  @Post('batches/complete')
  async completeBatchByBody(@Body() dto: { partial: string }) {
    return this.markBatchCompleted(dto.partial);
  }

  private async markBatchCompleted(guid: string) {
    await this.prisma.partialBatch.update({
      where: { id: guid },
      data: { status: 1 },
    });
    return { success: true, message: 'Batch completed', result: {} };
  }

  @Get('batches/:guid/boxes')
  async batchBoxes(@Param('guid') guid: string) {
    return this.findBatchBoxes(guid);
  }

  @Get('batches/boxes')
  async batchBoxesByQuery(@Query('guid') guid: string) {
    return this.findBatchBoxes(guid);
  }

  private async findBatchBoxes(guid: string) {
    const boxes = await this.prisma.warehouseBox.findMany({
      where: { partialId: guid },
      include: { partial: true, _count: { select: { orders: true } } },
      orderBy: { createdAt: 'asc' },
    });
    return boxes.map((box) => this.boxResponse(box));
  }

  @Post('batches/:guid/boxes')
  async addBoxToBatch(
    @Param('guid') guid: string,
    @Body() dto: MobileBatchBoxDto,
  ) {
    return this.assignBoxToBatch(guid, dto.box_guid);
  }

  @Post('batches/boxes')
  async addBoxToBatchByBody(@Body() dto: { guid: string; partial: string }) {
    return this.assignBoxToBatch(dto.partial, dto.guid);
  }

  private async assignBoxToBatch(batchGuid: string, boxGuid: string) {
    const box = await this.prisma.warehouseBox.update({
      where: { id: boxGuid },
      data: { partialId: batchGuid },
    });
    return {
      success: true,
      guid: box.id,
      message: 'Box added to batch',
      result: [{ UpdatedCodeID: box.codeId }],
    };
  }

  @Delete('batches/:guid/boxes/:boxGuid')
  async removeBoxFromBatch(@Param('boxGuid') boxGuid: string) {
    return this.unassignBoxFromBatch(boxGuid);
  }

  @Delete('batches/boxes')
  async removeBoxFromBatchByBody(@Body() dto: { guid?: string; box?: string }) {
    return this.unassignBoxFromBatch(dto.guid || dto.box || '');
  }

  private async unassignBoxFromBatch(boxGuid: string) {
    await this.prisma.warehouseBox.update({
      where: { id: boxGuid },
      data: { partialId: null },
    });
    return { success: true, message: 'Box removed from batch' };
  }

  private async assignOrderToBox(dto: BoxOrderDto) {
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
    return { success: true, guid: saved.id, message: 'Order added to box' };
  }

  private findBox(value: string) {
    const normalized = value.trim();
    return this.prisma.warehouseBox.findFirst({
      where: isUUID(normalized)
        ? { OR: [{ id: normalized }, { trackingCode: normalized }] }
        : { trackingCode: normalized },
    });
  }

  private async findComposition(dto: SaveOrderDto) {
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
    return composition;
  }

  private async platformSettings() {
    const config = await this.prisma.clientCodeConfig.findUnique({
      where: { id: 1 },
    });
    return {
      warehouseName: config?.warehouseName ?? 'BMG',
      country: config?.country ?? 'Espana',
    };
  }

  private receiverResponse(receiver: {
    id: string;
    clientCode: string;
    firstName: string;
    lastName: string;
    packageShoes: number;
    bagPackage: number;
  }) {
    return {
      guid: receiver.id,
      client_code: receiver.clientCode,
      full_name: `${receiver.firstName} ${receiver.lastName}`.trim(),
      package_shoes: receiver.packageShoes,
      bag_package: receiver.bagPackage,
    };
  }

  private orderResponse(order: {
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
    statusName: string;
    receiverId: string | null;
    length: number | null;
    width: number | null;
    height: number | null;
    createdAt: Date;
    updatedAt: Date;
    boxId: string | null;
    box?: { trackingCode: string } | null;
    composition: { name: string } | null;
  }) {
    return {
      guid: order.id,
      tracking_number: order.trackingNumber,
      tracking_code: order.trackingNumber,
      client: order.clientName,
      client_code: order.clientCode,
      status_name: order.statusName,
      with_box: order.withBox,
      gross_weight: this.number(order.grossWeight),
      composition_guid: order.compositionId,
      composition: order.composition?.name ?? null,
      description: order.productDescription,
      declared_value: this.number(order.declaredValue),
      currency: order.currency,
      receiver_guid: order.receiverId,
      user_guid: order.receiverId,
      length: order.length,
      width: order.width,
      height: order.height,
      box_guid: order.boxId,
      box_tracking_code: order.box?.trackingCode ?? null,
      created_at: order.createdAt.toISOString(),
      updated_at: order.updatedAt.toISOString(),
    };
  }

  private boxResponse(box: {
    id: string;
    codeId: string;
    partialId: string | null;
    trackingCode: string;
    warehouse: string;
    warehouseGuid: string | null;
    width: number;
    height: number;
    length: number;
    volumeWeight: Prisma.Decimal;
    grossWeight: Prisma.Decimal | null;
    createdAt: Date;
    partial: { warehouseName: string; partialName: string } | null;
    _count?: { orders: number };
  }) {
    return {
      guid: box.id,
      codeid: box.codeId,
      batch_guid: box.partialId,
      partial: box.partialId ?? '',
      batch_name: box.partial?.partialName ?? null,
      partial_name: box.partial?.partialName ?? null,
      tracking_code: box.trackingCode,
      warehouse: box.warehouse,
      warehouse_name: box.partial?.warehouseName ?? box.warehouse,
      warehouse_guid: box.warehouseGuid,
      width: box.width,
      height: box.height,
      length: box.length,
      volume_weight: this.number(box.volumeWeight) ?? 0,
      v_weight: this.number(box.volumeWeight) ?? 0,
      gross_weight: this.number(box.grossWeight) ?? 0,
      created_at: box.createdAt.toISOString(),
      date_created: box.createdAt.toISOString(),
      orders_count: box._count?.orders ?? 0,
      orderCount: box._count?.orders ?? 0,
    };
  }

  private batchResponse(batch: {
    id: string;
    codeId: string;
    partialCode: string;
    partialName: string;
    warehouseName: string;
    status: number;
    grossWeight: Prisma.Decimal | null;
    createdAt: Date;
    boxes: { grossWeight: Prisma.Decimal | null }[];
  }) {
    return {
      guid: batch.id,
      codeid: batch.codeId,
      batch_code: batch.partialCode,
      partial_code: batch.partialCode,
      batch_name: batch.partialName,
      partial_name: batch.partialName,
      partial: batch.id,
      warehouse_name: batch.warehouseName,
      status: batch.status,
      boxes_count: batch.boxes.length,
      boxCount: batch.boxes.length,
      gross_weight:
        this.number(batch.grossWeight) ??
        batch.boxes.reduce(
          (sum, box) => sum + (this.number(box.grossWeight) ?? 0),
          0,
        ),
      created_at: batch.createdAt.toISOString(),
      date_created: batch.createdAt.toISOString(),
    };
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
      )
        return code;
    }
    throw new ServiceUnavailableException(
      'Не удалось создать уникальный номер клиента. Повторите попытку',
    );
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
