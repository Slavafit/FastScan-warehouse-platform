import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaPg } from '@prisma/adapter-pg';
import { hash } from 'bcryptjs';
import { PrismaClient, Role } from './generated/prisma/client.js';

@Injectable()
export class PrismaService
  extends PrismaClient
  implements OnModuleInit, OnModuleDestroy
{
  private readonly logger = new Logger(PrismaService.name);
  private readonly config: ConfigService;

  constructor(config: ConfigService) {
    super({
      adapter: new PrismaPg({
        connectionString: config.getOrThrow<string>('DATABASE_URL'),
      }),
    });
    this.config = config;
  }

  async onModuleInit() {
    await this.$connect();
    await this.clientCodeConfig.upsert({
      where: { id: 1 },
      update: {},
      create: {
        id: 1,
        codePrefix: 'KG',
        warehouseName: 'BMG',
        country: 'España',
      },
    });
    await this.ensureBootstrapAdmin();
  }

  private async ensureBootstrapAdmin() {
    const existingAdmin = await this.user.findFirst({
      where: { role: Role.ADMIN },
      select: { id: true },
    });
    if (existingAdmin) return;

    const login = this.config
      .get<string>('BOOTSTRAP_ADMIN_LOGIN', '')
      .trim()
      .toLowerCase();
    const password = this.config.get<string>('BOOTSTRAP_ADMIN_PASSWORD', '');
    if (!login || password.length < 8) {
      throw new Error(
        'No administrator exists. Set BOOTSTRAP_ADMIN_LOGIN and BOOTSTRAP_ADMIN_PASSWORD (minimum 8 characters).',
      );
    }
    if (await this.user.findUnique({ where: { login } })) {
      throw new Error(
        `Cannot create bootstrap administrator: login "${login}" already exists with another role.`,
      );
    }
    await this.user.create({
      data: {
        login,
        passwordHash: await hash(password, 12),
        role: Role.ADMIN,
        enabled: true,
      },
    });
    this.logger.log(`Initial administrator "${login}" created`);
  }

  async onModuleDestroy() {
    await this.$disconnect();
  }
}
