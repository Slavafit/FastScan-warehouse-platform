import { CommonController } from './common.controller.js';
import { WarehouseController } from './warehouse.controller.js';
import { PrismaService } from './prisma.service.js';
import { NotificationService } from './notification.service.js';

describe('Mobile API contract', () => {
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
