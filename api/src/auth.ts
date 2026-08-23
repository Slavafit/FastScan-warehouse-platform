import {
  CanActivate,
  ExecutionContext,
  Injectable,
  SetMetadata,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { Reflector } from '@nestjs/core';
import { AuthGuard } from '@nestjs/passport';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { Role } from './generated/prisma/client.js';

export type AuthUser = {
  sub: string;
  login: string;
  role: Role;
  type: 'access' | 'refresh';
};

export const IS_PUBLIC_KEY = 'isPublic';
export const ROLES_KEY = 'roles';
export const Public = () => SetMetadata(IS_PUBLIC_KEY, true);
export const Roles = (...roles: Role[]) => SetMetadata(ROLES_KEY, roles);

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor(config: ConfigService) {
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      secretOrKey: config.getOrThrow<string>('JWT_SECRET'),
    });
  }

  validate(payload: AuthUser) {
    if (payload.type !== 'access') throw new UnauthorizedException();
    return payload;
  }
}

@Injectable()
export class JwtAuthGuard extends AuthGuard('jwt') {
  constructor(private readonly reflector: Reflector) {
    super();
  }

  canActivate(context: ExecutionContext) {
    if (
      this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
        context.getHandler(),
        context.getClass(),
      ])
    )
      return true;
    return super.canActivate(context);
  }
}

@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const roles = this.reflector.getAllAndOverride<Role[]>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (!roles?.length) return true;
    const request = context.switchToHttp().getRequest<{ user?: AuthUser }>();
    return !!request.user && roles.includes(request.user.role);
  }
}

@Injectable()
export class TokenService {
  constructor(
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
  ) {}

  async pair(user: { id: string; login: string; role: Role }) {
    const accessMinutes = this.config.get<number>('JWT_ACCESS_MINUTES', 30);
    const refreshDays = this.config.get<number>('JWT_REFRESH_DAYS', 30);
    return {
      success: true,
      accessToken: await this.jwt.signAsync(
        { sub: user.id, login: user.login, role: user.role, type: 'access' },
        { expiresIn: `${accessMinutes}m` as never },
      ),
      refreshToken: await this.jwt.signAsync(
        { sub: user.id, login: user.login, role: user.role, type: 'refresh' },
        { expiresIn: `${refreshDays}d` as never },
      ),
      role: user.role,
    };
  }

  async verifyRefresh(token: string): Promise<AuthUser> {
    const payload = await this.jwt.verifyAsync<AuthUser>(token, {
      secret: this.config.getOrThrow<string>('JWT_SECRET'),
    });
    if (payload.type !== 'refresh') throw new UnauthorizedException();
    return payload;
  }
}
