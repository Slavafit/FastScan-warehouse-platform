import {
  CallHandler,
  ExecutionContext,
  Injectable,
  NestInterceptor,
} from '@nestjs/common';
import { Observable, tap } from 'rxjs';
import { AuthUser } from './auth.js';
import { PrismaService } from './prisma.service.js';

@Injectable()
export class AuditInterceptor implements NestInterceptor {
  constructor(private readonly prisma: PrismaService) {}

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const request = context.switchToHttp().getRequest<{
      method: string;
      originalUrl: string;
      ip?: string;
      headers: Record<string, string | string[] | undefined>;
      user?: AuthUser;
      body?: Record<string, unknown>;
    }>();
    if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method))
      return next.handle();
    const startedAt = Date.now();
    return next.handle().pipe(
      tap({
        next: (response: unknown) => {
          const responseObject =
            typeof response === 'object' && response !== null
              ? (response as Record<string, unknown>)
              : {};
          const rawEntityId = responseObject.guid ?? request.body?.guid;
          void this.prisma.operationLog
            .create({
              data: {
                userId: request.user?.sub,
                action: request.method,
                route: request.originalUrl,
                entityType: this.entityType(request.originalUrl),
                entityId:
                  typeof rawEntityId === 'string' ||
                  typeof rawEntityId === 'number'
                    ? String(rawEntityId)
                    : null,
                details: {
                  duration_ms: Date.now() - startedAt,
                  success: true,
                },
                ipAddress: request.ip,
                userAgent: String(request.headers['user-agent'] ?? ''),
              },
            })
            .catch(() => undefined);
        },
        error: (error: unknown) => {
          const errorCode =
            typeof error === 'object' &&
            error !== null &&
            'code' in error &&
            typeof error.code === 'string'
              ? error.code
              : error instanceof Error
                ? error.name
                : 'UNKNOWN_ERROR';
          void this.prisma.operationLog
            .create({
              data: {
                userId: request.user?.sub,
                action: request.method,
                route: request.originalUrl,
                entityType: this.entityType(request.originalUrl),
                details: {
                  duration_ms: Date.now() - startedAt,
                  success: false,
                  error_code: errorCode,
                },
                ipAddress: request.ip,
                userAgent: String(request.headers['user-agent'] ?? ''),
              },
            })
            .catch(() => undefined);
        },
      }),
    );
  }

  private entityType(route: string) {
    if (route.includes('box')) return 'box';
    if (route.includes('partial')) return 'partial';
    if (route.includes('reciever')) return 'receiver';
    if (route.includes('notification')) return 'notification';
    if (route.includes('auth')) return 'authentication';
    if (route.includes('order')) return 'order';
    return 'system';
  }
}
