import { INestApplication } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from './../src/app.module.js';
import { PrismaService } from './../src/prisma.service.js';

interface HealthResponse {
  status: string;
  service: string;
  version: string;
  uptime_seconds: number;
  checks: {
    api: { status: string };
    database?: { status: string; response_time_ms: number };
  };
}

interface LoginResponse {
  role: string;
  accessToken: string;
  refreshToken: string;
}

describe('FastScan API (e2e)', () => {
  let app: INestApplication<App>;
  let prisma: PrismaService;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleFixture.createNestApplication();
    prisma = moduleFixture.get(PrismaService);
    await app.init();
  });

  it('reports service health', () => {
    return request(app.getHttpServer())
      .get('/health')
      .expect(200)
      .expect(({ body }) => {
        const health = body as HealthResponse;
        expect(health.status).toBe('ok');
        expect(health.service).toBe('fastscan-api');
        expect(health.version).toBe('0.4.0');
        expect(health.uptime_seconds).toBeGreaterThanOrEqual(0);
        expect(health.checks.api.status).toBe('up');
        expect(health.checks.database?.status).toBe('up');
        expect(health.checks.database?.response_time_ms).toBeGreaterThanOrEqual(
          0,
        );
      });
  });

  it('reports process liveness separately from dependencies', () => {
    return request(app.getHttpServer())
      .get('/health/live')
      .expect(200)
      .expect(({ body }) => {
        const health = body as HealthResponse;
        expect(health.status).toBe('ok');
        expect(health.checks.api.status).toBe('up');
        expect(health.checks.database).toBeUndefined();
      });
  });

  it('logs in and accesses a protected database route', async () => {
    const login = await request(app.getHttpServer())
      .post('/api/admin/auth/login')
      .send({ login: 'admin', password: 'change-me' })
      .expect(201);

    const auth = login.body as LoginResponse;
    expect(auth.role).toBe('ADMIN');
    expect(auth.accessToken).toBeTruthy();
    expect(auth.refreshToken).toBeTruthy();

    await request(app.getHttpServer())
      .get('/api/admin/client-code-settings')
      .set('Authorization', `Bearer ${auth.accessToken}`)
      .expect(200)
      .expect(({ body }) => {
        const settings = body as {
          prefix: string;
          warehouse: string;
          country: string;
        };
        expect(settings.prefix).toBe('KG');
        expect(settings.warehouse).toBe('BMG');
        expect(settings.country).toBe('España');
      });
  });

  it('rejects protected routes without a token', () => {
    return request(app.getHttpServer())
      .get('/api/order/box')
      .expect(401)
      .expect(({ body, headers }) => {
        const error = body as { request_id: string };
        expect(error).toMatchObject({
          statusCode: 401,
          error: 'Unauthorized',
          code: 'UNAUTHORIZED',
          message: 'Требуется авторизация',
          path: '/api/order/box',
          method: 'GET',
        });
        expect(error.request_id).toBeTruthy();
        expect(headers['x-request-id']).toBe(error.request_id);
      });
  });

  it('returns clear validation errors and registration conflicts', async () => {
    await request(app.getHttpServer())
      .post('/api/mobile/auth/login')
      .send({})
      .expect(400)
      .expect(({ body }) => {
        const error = body as {
          details: {
            fields: Array<{ field: string; messages: string[] }>;
          };
        };
        expect(error).toMatchObject({
          statusCode: 400,
          code: 'VALIDATION_ERROR',
          message: 'Проверьте данные запроса',
        });
        const fields = new Map(
          error.details.fields.map((item) => [item.field, item.messages]),
        );
        expect(fields.get('login')).toContain('Поле обязательно');
        expect(fields.get('password')).toContain('Поле обязательно');
      });

    await request(app.getHttpServer())
      .post('/api/client/auth/register')
      .send({
        client_code: 'KGTEST',
        email: 'another-client@example.com',
        password: 'change-me',
      })
      .expect(409)
      .expect(({ body }) => {
        expect(body).toMatchObject({
          statusCode: 409,
          code: 'CONFLICT',
          message: 'Client account already exists',
        });
      });
  });

  it('seeds admin, worker and client test accounts', async () => {
    await request(app.getHttpServer())
      .post('/api/admin/auth/login')
      .send({ login: 'admin', password: 'change-me' })
      .expect(201)
      .expect(({ body }) => {
        expect((body as LoginResponse).role).toBe('ADMIN');
      });

    await request(app.getHttpServer())
      .post('/api/admin/auth/login')
      .send({ login: 'worker', password: 'change-me' })
      .expect(201)
      .expect(({ body }) => {
        expect((body as LoginResponse).role).toBe('WORKER');
      });

    await request(app.getHttpServer())
      .post('/api/client/auth/login')
      .send({ login: 'client@test.com', password: 'change-me' })
      .expect(201)
      .expect(({ body }) => {
        expect((body as LoginResponse).role).toBe('CLIENT');
      });
  });

  it('uses WORKER for staff permissions and rejects a MANAGER role', async () => {
    const adminLogin = await request(app.getHttpServer())
      .post('/api/admin/auth/login')
      .send({ login: 'admin', password: 'change-me' })
      .expect(201);
    const admin = adminLogin.body as LoginResponse;
    const workerLogin = `e2e-worker-${Date.now()}`;

    await request(app.getHttpServer())
      .post('/api/admin/users')
      .set('Authorization', `Bearer ${admin.accessToken}`)
      .send({ login: workerLogin, password: 'test-password', role: 'MANAGER' })
      .expect(400);

    try {
      await request(app.getHttpServer())
        .post('/api/admin/users')
        .set('Authorization', `Bearer ${admin.accessToken}`)
        .send({ login: workerLogin, password: 'test-password', role: 'WORKER' })
        .expect(201)
        .expect(({ body }) => {
          expect((body as { role: string }).role).toBe('WORKER');
        });

      const workerLoginResponse = await request(app.getHttpServer())
        .post('/api/admin/auth/login')
        .send({ login: workerLogin, password: 'test-password' })
        .expect(201);
      const worker = workerLoginResponse.body as LoginResponse;

      await request(app.getHttpServer())
        .get('/api/staff/clients')
        .set('Authorization', `Bearer ${worker.accessToken}`)
        .expect(200);

      await request(app.getHttpServer())
        .get('/api/order/partial?typePartial=1')
        .set('Authorization', `Bearer ${worker.accessToken}`)
        .expect(200);

      await request(app.getHttpServer())
        .get('/api/common/order-compositions')
        .set('Authorization', `Bearer ${worker.accessToken}`)
        .expect(200)
        .expect(({ body }) => {
          const items = body as Array<{ code: string; name: string }>;
          expect(items.some((item) => item.code === 'CLOTHING')).toBe(true);
        });

      await request(app.getHttpServer())
        .get('/api/admin/client-code-settings')
        .set('Authorization', `Bearer ${worker.accessToken}`)
        .expect(403);
    } finally {
      await prisma.user.deleteMany({ where: { login: workerLogin } });
    }
  });

  it('runs the complete client-to-shipped-batch warehouse flow', async () => {
    const suffix = `${Date.now()}-${Math.floor(Math.random() * 10_000)}`;
    const clientTracking = `E2E-CLIENT-${suffix}`;
    const mobileTracking = `E2E-MOBILE-${suffix}`;
    const boxTracking = `E2E-BOX-${suffix}`;
    const batchCode = `E2E-BATCH-${suffix}`;
    let clientOrderId = '';
    let mobileOrderId = '';
    let boxId = '';
    let batchId = '';

    const clientLogin = await request(app.getHttpServer())
      .post('/api/client/auth/login')
      .send({ login: 'client@test.com', password: 'change-me' })
      .expect(201);
    const client = clientLogin.body as LoginResponse;

    const workerLogin = await request(app.getHttpServer())
      .post('/api/mobile/auth/login')
      .send({ login: 'worker', password: 'change-me' })
      .expect(201);
    const worker = workerLogin.body as LoginResponse;

    const clientAuth = `Bearer ${client.accessToken}`;
    const workerAuth = `Bearer ${worker.accessToken}`;

    const profileResponse = await request(app.getHttpServer())
      .get('/api/client/profile')
      .set('Authorization', clientAuth)
      .expect(200);
    const profile = profileResponse.body as {
      client_code: string;
      full_name: string;
    };

    try {
      const clientOrderResponse = await request(app.getHttpServer())
        .post('/api/client/orders')
        .set('Authorization', clientAuth)
        .send({
          tracking_number: clientTracking,
          description: 'Client pre-registered test order',
          declared_value: 25.5,
          currency: 'EUR',
        })
        .expect(201);
      const clientOrder = clientOrderResponse.body as {
        guid: string;
        tracking_number: string;
        status_name: string;
      };
      clientOrderId = clientOrder.guid;
      expect(clientOrder.tracking_number).toBe(clientTracking);
      expect(clientOrder.status_name).toBe('CLIENT_REGISTERED');

      const mobileOrderResponse = await request(app.getHttpServer())
        .post('/api/mobile/orders')
        .set('Authorization', workerAuth)
        .send({
          client_code: profile.client_code,
          client: profile.full_name,
          tracking_number: mobileTracking,
          gross_weight: 2.4,
          with_box: 0,
          length: 30,
          width: 20,
          height: 10,
          description: 'Mobile warehouse test order',
          declared_value: 42,
          currency: 'EUR',
        })
        .expect(201);
      const mobileOrder = mobileOrderResponse.body as {
        success: boolean;
        guid: string;
      };
      mobileOrderId = mobileOrder.guid;
      expect(mobileOrder.success).toBe(true);

      const boxResponse = await request(app.getHttpServer())
        .post('/api/mobile/boxes')
        .set('Authorization', workerAuth)
        .send({
          tracking_code: boxTracking,
          width: 50,
          height: 40,
          length: 60,
          v_weight: 24,
          gross_weight: 3.2,
        })
        .expect(201);
      const box = boxResponse.body as { success: boolean; guid: string };
      boxId = box.guid;
      expect(box.success).toBe(true);

      await request(app.getHttpServer())
        .get(`/api/mobile/orders/${encodeURIComponent(clientTracking)}`)
        .set('Authorization', workerAuth)
        .expect(200)
        .expect(({ body }) => {
          const order = body as { guid: string; tracking_number: string };
          expect(order.guid).toBe(clientOrderId);
          expect(order.tracking_number).toBe(clientTracking);
        });

      await request(app.getHttpServer())
        .get('/api/mobile/orders')
        .query({ tracking_number: mobileTracking })
        .set('Authorization', workerAuth)
        .expect(200)
        .expect(({ body }) => {
          const orders = body as Array<{ guid: string }>;
          expect(orders).toHaveLength(1);
          expect(orders[0]?.guid).toBe(mobileOrderId);
        });

      await request(app.getHttpServer())
        .get('/api/mobile/receivers')
        .query({ client_code: profile.client_code })
        .set('Authorization', workerAuth)
        .expect(200)
        .expect(({ body }) => {
          const receivers = body as Array<{ client_code: string }>;
          expect(receivers).toHaveLength(1);
          expect(receivers[0]?.client_code).toBe(profile.client_code);
        });

      const batchResponse = await request(app.getHttpServer())
        .post('/api/mobile/batches')
        .set('Authorization', workerAuth)
        .send({ partial_code: batchCode, partial_name: `E2E ${suffix}` })
        .expect(201);
      const batch = batchResponse.body as { success: boolean; guid: string };
      batchId = batch.guid;
      expect(batch.success).toBe(true);

      await request(app.getHttpServer())
        .post(`/api/mobile/boxes/${boxId}/orders`)
        .set('Authorization', workerAuth)
        .send({
          order_guid: mobileOrderId,
          description: 'Packed by complete E2E test',
        })
        .expect(201)
        .expect(({ body }) => {
          expect((body as { success: boolean }).success).toBe(true);
        });

      await request(app.getHttpServer())
        .get(`/api/mobile/boxes/${boxId}/orders`)
        .set('Authorization', workerAuth)
        .expect(200)
        .expect(({ body }) => {
          const orders = body as Array<{
            guid: string;
            box_tracking_code: string;
          }>;
          expect(orders).toHaveLength(1);
          expect(orders[0]?.guid).toBe(mobileOrderId);
          expect(orders[0]?.box_tracking_code).toBe(boxTracking);
        });

      await request(app.getHttpServer())
        .get('/api/mobile/orders')
        .query({ box_tracking_code: boxTracking })
        .set('Authorization', workerAuth)
        .expect(200)
        .expect(({ body }) => {
          const orders = body as Array<{ guid: string }>;
          expect(orders).toHaveLength(1);
          expect(orders[0]?.guid).toBe(mobileOrderId);
        });

      await request(app.getHttpServer())
        .post(`/api/mobile/batches/${batchId}/boxes`)
        .set('Authorization', workerAuth)
        .send({ box_guid: boxId })
        .expect(201)
        .expect(({ body }) => {
          expect((body as { success: boolean }).success).toBe(true);
        });

      await request(app.getHttpServer())
        .get(`/api/mobile/batches/${batchId}/boxes`)
        .set('Authorization', workerAuth)
        .expect(200)
        .expect(({ body }) => {
          const boxes = body as Array<{ guid: string; partial: string }>;
          expect(boxes).toHaveLength(1);
          expect(boxes[0]?.guid).toBe(boxId);
          expect(boxes[0]?.partial).toBe(batchId);
        });

      await request(app.getHttpServer())
        .post(`/api/mobile/batches/${batchId}/complete`)
        .set('Authorization', workerAuth)
        .expect(201)
        .expect(({ body }) => {
          expect((body as { success: boolean }).success).toBe(true);
        });

      await request(app.getHttpServer())
        .get('/api/mobile/batches')
        .query({ status: 1 })
        .set('Authorization', workerAuth)
        .expect(200)
        .expect(({ body }) => {
          const batches = body as Array<{
            guid: string;
            status: number;
            boxes_count: number;
          }>;
          const completed = batches.find((item) => item.guid === batchId);
          expect(completed).toMatchObject({ status: 1, boxes_count: 1 });
        });
    } finally {
      await prisma.clientNotification.deleteMany({
        where: {
          OR: [
            { message: { contains: clientTracking } },
            { message: { contains: mobileTracking } },
            { message: { contains: boxTracking } },
          ],
        },
      });
      await prisma.shipmentOrder.deleteMany({
        where: { trackingNumber: { in: [clientTracking, mobileTracking] } },
      });
      if (boxId) await prisma.warehouseBox.deleteMany({ where: { id: boxId } });
      if (batchId)
        await prisma.partialBatch.deleteMany({ where: { id: batchId } });
    }
  });

  afterAll(async () => {
    await app.close();
  });
});
