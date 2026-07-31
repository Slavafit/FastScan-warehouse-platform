import { INestApplication } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from './../src/app.module.js';

interface HealthResponse {
  status: string;
  service: string;
}

interface LoginResponse {
  role: string;
  accessToken: string;
  refreshToken: string;
}

describe('FastScan API (e2e)', () => {
  let app: INestApplication<App>;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleFixture.createNestApplication();
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

  afterAll(async () => {
    await app.close();
  });
});
