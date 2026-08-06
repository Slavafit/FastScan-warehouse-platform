import { ClientController } from './client.controller.js';
import { MobileController } from './mobile.controller.js';
import { NotificationService } from './notification.service.js';
import { PrismaService } from './prisma.service.js';

describe('Client shipment registration', () => {
  it('lets a client pre-register a shipment that mobile can find later', async () => {
    const account = {
      id: 'account-1',
      userId: 'user-1',
      receiverId: 'receiver-1',
      email: 'client@example.com',
      phone: null,
      address: null,
      emailNotifications: true,
      pushNotifications: true,
      createdAt: new Date('2026-08-06T10:00:00.000Z'),
      updatedAt: new Date('2026-08-06T10:00:00.000Z'),
      receiver: {
        id: 'receiver-1',
        clientCode: 'KG123456',
        firstName: 'Anna',
        lastName: 'Client',
        packageShoes: 0,
        bagPackage: 0,
      },
    };
    const registeredOrder = {
      id: 'order-1',
      trackingNumber: 'TRACK-1',
      clientName: 'Anna Client',
      clientCode: 'KG123456',
      statusName: 'CLIENT_REGISTERED',
      withBox: 0,
      grossWeight: null,
      compositionId: 'composition-1',
      composition: { name: 'Clothes' },
      productDescription: 'Jacket',
      declaredValue: { toString: () => '49.90' },
      currency: 'EUR',
      receiverId: 'receiver-1',
      length: null,
      width: null,
      height: null,
      boxId: null,
      box: null,
      createdAt: new Date('2026-08-06T10:00:00.000Z'),
      updatedAt: new Date('2026-08-06T10:00:00.000Z'),
    };
    const prisma = {
      clientAccount: {
        findUnique: jest.fn().mockResolvedValue(account),
      },
      shipmentOrder: {
        findUnique: jest
          .fn()
          .mockResolvedValueOnce(null)
          .mockResolvedValueOnce(registeredOrder),
        upsert: jest.fn().mockResolvedValue(registeredOrder),
      },
      orderComposition: {
        findUnique: jest
          .fn()
          .mockResolvedValue({ id: 'composition-1', enabled: true }),
      },
    } as unknown as PrismaService;
    const clientController = new ClientController(prisma);
    const mobileController = new MobileController(
      prisma,
      {} as NotificationService,
    );

    await expect(
      clientController.createOrder(
        { user: { sub: 'user-1' } } as never,
        {
          tracking_number: ' TRACK-1 ',
          composition_guid: 'composition-1',
          description: 'Jacket',
          declared_value: 49.9,
          currency: 'eur',
        },
      ),
    ).resolves.toMatchObject({
      tracking_number: 'TRACK-1',
      status_name: 'CLIENT_REGISTERED',
      composition: 'Clothes',
      description: 'Jacket',
      declared_value: 49.9,
      currency: 'EUR',
    });
    expect(prisma.shipmentOrder.upsert).toHaveBeenCalledWith(
      expect.objectContaining({
        create: expect.objectContaining({
          trackingNumber: 'TRACK-1',
          receiverId: 'receiver-1',
          clientCode: 'KG123456',
          statusName: 'CLIENT_REGISTERED',
        }),
      }),
    );

    await expect(mobileController.orderByTracking('TRACK-1')).resolves.toMatchObject({
      tracking_number: 'TRACK-1',
      status_name: 'CLIENT_REGISTERED',
      client_code: 'KG123456',
      client: 'Anna Client',
      composition: 'Clothes',
      description: 'Jacket',
      declared_value: 49.9,
      currency: 'EUR',
    });
  });
});
