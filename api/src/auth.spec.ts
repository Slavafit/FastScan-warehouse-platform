import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { Role } from './generated/prisma/client.js';
import { TokenService } from './auth.js';

describe('TokenService', () => {
  const config = {
    get: (key: string, fallback: unknown) =>
      ({
        JWT_ACCESS_MINUTES: 30,
        JWT_REFRESH_DAYS: 30,
      })[key] ?? fallback,
    getOrThrow: () => 'test-secret-with-at-least-32-characters',
  } as unknown as ConfigService;
  const jwt = new JwtService({
    secret: 'test-secret-with-at-least-32-characters',
  });
  const service = new TokenService(jwt, config);

  it('creates compatible access and refresh tokens', async () => {
    const pair = await service.pair({
      id: 'user-1',
      login: 'admin',
      role: Role.ADMIN,
    });
    expect(pair.success).toBe(true);
    expect(pair.role).toBe(Role.ADMIN);
    expect(pair.accessToken).toBeTruthy();
    const refresh = await service.verifyRefresh(pair.refreshToken);
    expect(refresh.login).toBe('admin');
    expect(refresh.type).toBe('refresh');
  });
});
