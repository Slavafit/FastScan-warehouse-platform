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
    return request(app.getHttpServer()).get('/api/order/box').expect(401);
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
        .get('/api/admin/client-code-settings')
        .set('Authorization', `Bearer ${worker.accessToken}`)
        .expect(403);
    } finally {
      await prisma.user.deleteMany({ where: { login: workerLogin } });
    }
  });

  afterAll(async () => {
    await app.close();
  });
});
