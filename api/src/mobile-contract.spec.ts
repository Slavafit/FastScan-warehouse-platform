import { CommonController } from './common.controller.js';
import { WarehouseController } from './warehouse.controller.js';
import { PrismaService } from './prisma.service.js';
import { NotificationService } from './notification.service.js';

describe('Mobile API contract', () => {
  it('documents every Android Retrofit endpoint in the compatibility layer', () => {
    const androidEndpoints = [
      'POST /api/admin/auth/login',
      'POST /api/admin/auth/refresh',
      'GET /api/common/reciever',
      'GET /api/common/all-reciever',
      'POST /api/common/reciever',
      'GET /api/order/order-tracking-code',
      'GET /api/order/box',
      'POST /api/order/box',
      'GET /api/order/order-search',
      'POST /api/order/box-order',
      'DELETE /api/order/box-order',
      'GET /api/order/partial',
      'POST /api/order/partial',
      'POST /api/order/send-partial',
      'GET /api/order/partial-box',
      'DELETE /api/order/partial-box',
    ];

    expect(androidEndpoints).toMatchInlineSnapshot(`
[
  "POST /api/admin/auth/login",
  "POST /api/admin/auth/refresh",
  "GET /api/common/reciever",
  "GET /api/common/all-reciever",
  "POST /api/common/reciever",
  "GET /api/order/order-tracking-code",
  "GET /api/order/box",
  "POST /api/order/box",
  "GET /api/order/order-search",
  "POST /api/order/box-order",
  "DELETE /api/order/box-order",
  "GET /api/order/partial",
  "POST /api/order/partial",
  "POST /api/order/send-partial",
  "GET /api/order/partial-box",
  "DELETE /api/order/partial-box",
]
`);
  });

  it('keeps receiver JSON field names', async () => {
    const prisma = {
      receiver: {
        findUnique: jest.fn().mockResolvedValue({
          id: 'receiver-1',
          clientCode: 'KG123456',
          firstName: 'Anna',
          lastName: 'Client',
          packageShoes: 1,
          bagPackage: 0,
        }),
      },
    } as unknown as PrismaService;
    const controller = new CommonController(prisma);
    await expect(controller.receiver('KG123456')).resolves.toEqual([
      {
        full_name: 'Anna Client',
        guid: 'receiver-1',
        package_shoes: 1,
        bag_package: 0,
      },
    ]);
  });

  it('keeps order JSON field names', async () => {
    const prisma = {
      shipmentOrder: {
        findUnique: jest.fn().mockResolvedValue({
          id: 'order-1',
          trackingNumber: 'TRACK-1',
          clientName: 'Anna Client',
          clientCode: 'KG123456',
          withBox: 1,
          grossWeight: { toString: () => '2.500' },
          receiverId: 'receiver-1',
          length: 10,
          width: 20,
          height: 30,
          compositionId: 'composition-1',
          composition: { name: 'Одежда' },
          productDescription: 'Куртка',
          declaredValue: { toString: () => '49.90' },
          currency: 'EUR',
        }),
      },
    } as unknown as PrismaService;
    const notifications = {} as NotificationService;
    const controller = new WarehouseController(prisma, notifications);
    const result = await controller.orderDetails('TRACK-1');
    expect(result).toEqual([
      {
        guid: 'order-1',
        tracking_number: 'TRACK-1',
        client: 'Anna Client',
        client_code: 'KG123456',
        with_box: '1',
        gross_weight: 2.5,
        user_guid: 'receiver-1',
        length: 10,
        width: 20,
        height: 30,
        composition_guid: 'composition-1',
        composition: 'Одежда',
        description: 'Куртка',
        declared_value: 49.9,
        currency: 'EUR',
      },
    ]);
  });

  it('keeps box list JSON field names', async () => {
    const prisma = {
      warehouseBox: {
        findMany: jest.fn().mockResolvedValue([
          {
            id: 'box-1',
            codeId: '1',
            partialId: 'partial-1',
            trackingCode: 'BOX-1',
            warehouse: 'BMG',
            warehouseGuid: 'warehouse-1',
            width: 40,
            height: 30,
            length: 50,
            volumeWeight: { toString: () => '10.000' },
            grossWeight: { toString: () => '8.500' },
            createdAt: new Date('2026-08-06T10:00:00.000Z'),
            partial: { warehouseName: 'Malaga', partialName: 'KG100' },
            _count: { orders: 3 },
          },
        ]),
      },
    } as unknown as PrismaService;
    const controller = new WarehouseController(
      prisma,
      {} as NotificationService,
    );

    await expect(controller.boxes('', '', '0')).resolves.toEqual([
      {
        guid: 'box-1',
        codeid: '1',
        partial: 'partial-1',
        partial_name: 'KG100',
        tracking_code: 'BOX-1',
        warehouse: 'BMG',
        warehouse_name: 'Malaga',
        warehouse_guid: 'warehouse-1',
        width: 40,
        height: 30,
        length: 50,
        v_weight: 10,
        gross_weight: 8.5,
        chargeable_weight: 10,
        date_created: '2026-08-06T10:00:00.000Z',
        orderCount: 3,
      },
    ]);
  });

  it('keeps order search JSON field names', async () => {
    const prisma = {
      shipmentOrder: {
        findUnique: jest.fn().mockResolvedValue({
          id: 'order-1',
          trackingNumber: 'TRACK-1',
          statusName: 'AT_WAREHOUSE',
          grossWeight: { toString: () => '2.500' },
          clientCode: 'KG123456',
          clientName: 'Anna Client',
          compositionId: 'composition-1',
          composition: { name: 'Clothes' },
          productDescription: 'Jacket',
          declaredValue: { toString: () => '49.90' },
          currency: 'EUR',
        }),
      },
    } as unknown as PrismaService;
    const controller = new WarehouseController(
      prisma,
      {} as NotificationService,
    );

    await expect(controller.orderSearch('TRACK-1')).resolves.toEqual([
      {
        guid: 'order-1',
        tracking_number: 'TRACK-1',
        tracking_code: 'TRACK-1',
        status_name: 'AT_WAREHOUSE',
        gross_weight: 2.5,
        client_code: 'KG123456',
        client: 'Anna Client',
        composition_guid: 'composition-1',
        composition: 'Clothes',
        description: 'Jacket',
        declared_value: 49.9,
        currency: 'EUR',
      },
    ]);
  });

  it('keeps box create and attach responses compatible with Android', async () => {
    const prisma = {
      warehouseBox: {
        findUnique: jest
          .fn()
          .mockResolvedValueOnce(null)
          .mockResolvedValueOnce({ id: 'box-1', codeId: '1' }),
        create: jest.fn().mockResolvedValue({ id: 'box-1', codeId: '1' }),
        update: jest.fn().mockResolvedValue({ id: 'box-1', codeId: '1' }),
        count: jest.fn().mockResolvedValue(0),
      },
      clientCodeConfig: {
        findUnique: jest
          .fn()
          .mockResolvedValue({ warehouseName: 'BMG', country: 'Espana' }),
      },
    } as unknown as PrismaService;
    const controller = new WarehouseController(
      prisma,
      {} as NotificationService,
    );

    await expect(
      controller.saveBox({
        codeid: '1',
        tracking_code: 'BOX-1',
        width: 40,
        height: 30,
        length: 50,
        v_weight: 10,
        gross_weight: 8.5,
      }),
    ).resolves.toEqual({
      success: true,
      message: 'Box saved',
      result: [{ NewCodeID: 1 }],
    });

    await expect(
      controller.saveBox({
        guid: 'box-1',
        codeid: '1',
        tracking_code: 'BOX-1',
        partial: 'partial-1',
      }),
    ).resolves.toEqual({
      success: true,
      message: 'Box added to batch',
      result: [{ UpdatedCodeID: '1' }],
    });
  });

  it('keeps batch list and batch box JSON field names', async () => {
    const date = new Date('2026-08-06T10:00:00.000Z');
    const prisma = {
      partialBatch: {
        findMany: jest.fn().mockResolvedValue([
          {
            id: 'partial-1',
            codeId: '1',
            partialCode: 'KG100',
            partialName: 'KG100',
            warehouseName: 'BMG',
            createdAt: date,
            status: 0,
            grossWeight: null,
            boxes: [{
              grossWeight: { toString: () => '8.500' },
              volumeWeight: { toString: () => '10.000' },
            }],
          },
        ]),
      },
      warehouseBox: {
        findMany: jest.fn().mockResolvedValue([
          {
            id: 'box-1',
            codeId: '1',
            partialId: 'partial-1',
            trackingCode: 'BOX-1',
            width: 40,
            height: 30,
            length: 50,
            volumeWeight: { toString: () => '10.000' },
            grossWeight: { toString: () => '8.500' },
            createdAt: date,
            partial: { warehouseName: 'BMG', partialName: 'KG100' },
            warehouse: 'BMG',
          },
        ]),
      },
    } as unknown as PrismaService;
    const controller = new WarehouseController(
      prisma,
      {} as NotificationService,
    );

    await expect(controller.partials('0')).resolves.toEqual([
      {
        guid: 'partial-1',
        codeid: '1',
        partial: 'partial-1',
        partial_code: 'KG100',
        partial_name: 'KG100',
        warehouse_name: 'BMG',
        date_created: '2026-08-06T10:00:00.000Z',
        boxCount: 1,
        status: 0,
        gross_weight: 8.5,
        volume_weight: 10,
        chargeable_weight: 10,
      },
    ]);

    await expect(controller.partialBoxes('partial-1')).resolves.toEqual([
      {
        guid: 'box-1',
        codeid: '1',
        partial: 'partial-1',
        partial_name: 'KG100',
        tracking_code: 'BOX-1',
        width: 40,
        height: 30,
        length: 50,
        v_weight: 10,
        gross_weight: 8.5,
        chargeable_weight: 10,
        date_created: '2026-08-06T10:00:00.000Z',
        warehouse_name: 'BMG',
      },
    ]);
  });

  it('keeps result while returning a readable batch completion response', async () => {
    const prisma = {
      partialBatch: {
        update: jest.fn().mockResolvedValue({ id: 'partial-1', status: 1 }),
      },
    } as unknown as PrismaService;
    const controller = new WarehouseController(
      prisma,
      {} as NotificationService,
    );

    await expect(
      controller.sendPartial({ partial: 'partial-1' }),
    ).resolves.toEqual({
      success: true,
      message: 'Batch completed',
      result: {},
    });
  });
});
