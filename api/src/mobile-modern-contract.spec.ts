import { MobileController } from './mobile.controller.js';
import { NotificationService } from './notification.service.js';
import { PrismaService } from './prisma.service.js';

describe('Modern mobile API contract', () => {
  it('documents the new Android endpoint names', () => {
    const endpoints = [
      'POST /api/mobile/auth/login',
      'POST /api/mobile/auth/refresh',
      'GET /api/mobile/receivers',
      'POST /api/mobile/receivers',
      'GET /api/mobile/orders/:trackingNumber',
      'GET /api/mobile/orders',
      'POST /api/mobile/orders',
      'GET /api/mobile/boxes',
      'POST /api/mobile/boxes',
      'PUT /api/mobile/boxes/:guid',
      'GET /api/mobile/boxes/:guid/orders',
      'POST /api/mobile/boxes/:guid/orders',
      'DELETE /api/mobile/boxes/:guid/orders/:orderGuid',
      'POST /api/mobile/boxes/orders',
      'DELETE /api/mobile/boxes/orders',
      'GET /api/mobile/batches',
      'POST /api/mobile/batches',
      'PUT /api/mobile/batches/:guid',
      'POST /api/mobile/batches/:guid/complete',
      'POST /api/mobile/batches/complete',
      'GET /api/mobile/batches/:guid/boxes',
      'POST /api/mobile/batches/:guid/boxes',
      'DELETE /api/mobile/batches/:guid/boxes/:boxGuid',
      'GET /api/mobile/batches/boxes',
      'POST /api/mobile/batches/boxes',
      'DELETE /api/mobile/batches/boxes',
    ];

    expect(endpoints).toMatchInlineSnapshot(`
[
  "POST /api/mobile/auth/login",
  "POST /api/mobile/auth/refresh",
  "GET /api/mobile/receivers",
  "POST /api/mobile/receivers",
  "GET /api/mobile/orders/:trackingNumber",
  "GET /api/mobile/orders",
  "POST /api/mobile/orders",
  "GET /api/mobile/boxes",
  "POST /api/mobile/boxes",
  "PUT /api/mobile/boxes/:guid",
  "GET /api/mobile/boxes/:guid/orders",
  "POST /api/mobile/boxes/:guid/orders",
  "DELETE /api/mobile/boxes/:guid/orders/:orderGuid",
  "POST /api/mobile/boxes/orders",
  "DELETE /api/mobile/boxes/orders",
  "GET /api/mobile/batches",
  "POST /api/mobile/batches",
  "PUT /api/mobile/batches/:guid",
  "POST /api/mobile/batches/:guid/complete",
  "POST /api/mobile/batches/complete",
  "GET /api/mobile/batches/:guid/boxes",
  "POST /api/mobile/batches/:guid/boxes",
  "DELETE /api/mobile/batches/:guid/boxes/:boxGuid",
  "GET /api/mobile/batches/boxes",
  "POST /api/mobile/batches/boxes",
  "DELETE /api/mobile/batches/boxes",
]
`);
  });

  it('returns modern receiver fields', async () => {
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
    const controller = new MobileController(prisma, {} as NotificationService);

    await expect(controller.receivers('kg123456')).resolves.toEqual([
      {
        guid: 'receiver-1',
        client_code: 'KG123456',
        full_name: 'Anna Client',
        package_shoes: 1,
        bag_package: 0,
      },
    ]);
  });

  it('returns modern order fields', async () => {
    const prisma = {
      shipmentOrder: {
        findUnique: jest.fn().mockResolvedValue({
          id: 'order-1',
          trackingNumber: 'TRACK-1',
          clientName: 'Anna Client',
          clientCode: 'KG123456',
          statusName: 'AT_WAREHOUSE',
          withBox: 1,
          grossWeight: { toString: () => '2.500' },
          compositionId: 'composition-1',
          composition: { name: 'Clothes' },
          productDescription: 'Jacket',
          declaredValue: { toString: () => '49.90' },
          currency: 'EUR',
          receiverId: 'receiver-1',
          length: 10,
          width: 20,
          height: 30,
          boxId: 'box-1',
          box: { trackingCode: 'BOX-1' },
          createdAt: new Date('2026-08-06T10:00:00.000Z'),
          updatedAt: new Date('2026-08-06T11:00:00.000Z'),
        }),
      },
    } as unknown as PrismaService;
    const controller = new MobileController(prisma, {} as NotificationService);

    await expect(controller.orderByTracking('TRACK-1')).resolves.toEqual({
      guid: 'order-1',
      tracking_number: 'TRACK-1',
      tracking_code: 'TRACK-1',
      client: 'Anna Client',
      client_code: 'KG123456',
      status_name: 'AT_WAREHOUSE',
      with_box: 1,
      gross_weight: 2.5,
      composition_guid: 'composition-1',
      composition: 'Clothes',
      description: 'Jacket',
      declared_value: 49.9,
      currency: 'EUR',
      receiver_guid: 'receiver-1',
      user_guid: 'receiver-1',
      length: 10,
      width: 20,
      height: 30,
      box_guid: 'box-1',
      box_tracking_code: 'BOX-1',
      created_at: '2026-08-06T10:00:00.000Z',
      updated_at: '2026-08-06T11:00:00.000Z',
    });
  });

  it('returns unpacked orders from PostgreSQL', async () => {
    const createdAt = new Date('2026-08-06T10:00:00.000Z');
    const prisma = {
      shipmentOrder: {
        findMany: jest.fn().mockResolvedValue([
          {
            id: 'order-1',
            trackingNumber: 'TRACK-1',
            clientName: 'Anna Client',
            clientCode: 'KG123456',
            statusName: 'AT_WAREHOUSE',
            withBox: 0,
            grossWeight: { toString: () => '2.500' },
            compositionId: null,
            composition: null,
            productDescription: null,
            declaredValue: null,
            currency: 'EUR',
            receiverId: 'receiver-1',
            length: 10,
            width: 20,
            height: 30,
            boxId: null,
            box: null,
            createdAt,
            updatedAt: createdAt,
          },
        ]),
      },
    } as unknown as PrismaService;
    const controller = new MobileController(prisma, {} as NotificationService);

    const result = await controller.orders(undefined, undefined, 'true', '7');

    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      tracking_number: 'TRACK-1',
      box_guid: null,
      created_at: '2026-08-06T10:00:00.000Z',
    });
  });

  it('returns modern box and batch fields', async () => {
    const date = new Date('2026-08-06T10:00:00.000Z');
    const prisma = {
      warehouseBox: {
        findMany: jest.fn().mockResolvedValue([
          {
            id: 'box-1',
            codeId: '1',
            partialId: 'batch-1',
            trackingCode: 'BOX-1',
            warehouse: 'BMG',
            warehouseGuid: 'warehouse-1',
            width: 40,
            height: 30,
            length: 50,
            volumeWeight: { toString: () => '10.000' },
            grossWeight: { toString: () => '8.500' },
            createdAt: date,
            partial: { warehouseName: 'Malaga', partialName: 'KG100' },
            _count: { orders: 3 },
          },
        ]),
      },
      partialBatch: {
        findMany: jest.fn().mockResolvedValue([
          {
            id: 'batch-1',
            codeId: '1',
            partialCode: 'KG100',
            partialName: 'KG100',
            warehouseName: 'BMG',
            status: 0,
            grossWeight: null,
            createdAt: date,
            boxes: [{ grossWeight: { toString: () => '8.500' } }],
          },
        ]),
      },
    } as unknown as PrismaService;
    const controller = new MobileController(prisma, {} as NotificationService);

    await expect(controller.boxes('', '', '0')).resolves.toEqual([
      {
        guid: 'box-1',
        codeid: '1',
        batch_guid: 'batch-1',
        partial: 'batch-1',
        batch_name: 'KG100',
        partial_name: 'KG100',
        tracking_code: 'BOX-1',
        warehouse: 'BMG',
        warehouse_name: 'Malaga',
        warehouse_guid: 'warehouse-1',
        width: 40,
        height: 30,
        length: 50,
        volume_weight: 10,
        v_weight: 10,
        gross_weight: 8.5,
        created_at: '2026-08-06T10:00:00.000Z',
        date_created: '2026-08-06T10:00:00.000Z',
        orders_count: 3,
        orderCount: 3,
      },
    ]);

    await expect(controller.batches('0')).resolves.toEqual([
      {
        guid: 'batch-1',
        codeid: '1',
        batch_code: 'KG100',
        partial_code: 'KG100',
        batch_name: 'KG100',
        partial_name: 'KG100',
        partial: 'batch-1',
        warehouse_name: 'BMG',
        status: 0,
        boxes_count: 1,
        boxCount: 1,
        gross_weight: 8.5,
        created_at: '2026-08-06T10:00:00.000Z',
        date_created: '2026-08-06T10:00:00.000Z',
      },
    ]);
  });

  it('searches an ordinary box tracking code without querying the UUID field', async () => {
    const findFirst = jest.fn().mockResolvedValue({ id: 'box-1' });
    const findMany = jest.fn().mockResolvedValue([]);
    const prisma = {
      warehouseBox: { findFirst },
      shipmentOrder: { findMany },
    } as unknown as PrismaService;
    const controller = new MobileController(prisma, {} as NotificationService);

    await expect(
      controller.orders(undefined, ' NBX081310500429 '),
    ).resolves.toEqual([]);

    expect(findFirst).toHaveBeenCalledWith({
      where: { trackingCode: 'NBX081310500429' },
    });
    expect(findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { boxId: 'box-1' } }),
    );
  });
});
