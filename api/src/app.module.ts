import { Module } from '@nestjs/common';
import { APP_GUARD, APP_INTERCEPTOR } from '@nestjs/core';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { JwtModule } from '@nestjs/jwt';
import { PassportModule } from '@nestjs/passport';
import { AdminController } from './admin.controller.js';
import { AuditInterceptor } from './audit.interceptor.js';
import { AuthController } from './auth.controller.js';
import { JwtAuthGuard, JwtStrategy, RolesGuard, TokenService } from './auth.js';
import { ClientController } from './client.controller.js';
import { CommonController } from './common.controller.js';
import { HealthController } from './health.controller.js';
import { MobileController } from './mobile.controller.js';
import { StaffController } from './staff.controller.js';
import { NotificationService } from './notification.service.js';
import { PrismaService } from './prisma.service.js';
import { WarehouseController } from './warehouse.controller.js';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    PassportModule,
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        secret: config.getOrThrow<string>('JWT_SECRET'),
      }),
    }),
  ],
  controllers: [
    HealthController,
    AuthController,
    CommonController,
    WarehouseController,
    MobileController,
    ClientController,
    StaffController,
    AdminController,
  ],
  providers: [
    PrismaService,
    NotificationService,
    TokenService,
    JwtStrategy,
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useClass: RolesGuard },
    { provide: APP_INTERCEPTOR, useClass: AuditInterceptor },
  ],
})
export class AppModule {}
