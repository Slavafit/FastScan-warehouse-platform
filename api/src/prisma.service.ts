import { Injectable, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaPg } from '@prisma/adapter-pg';
import { hash } from 'bcryptjs';
import { PrismaClient, Role } from './generated/prisma/client.js';

@Injectable()
export class PrismaService
  extends PrismaClient
  implements OnModuleInit, OnModuleDestroy
{
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
      create: { id: 1, codePrefix: 'KG' },
    });
    const login = this.config
      .get<string>('FASTSCAN_ADMIN_LOGIN', 'admin')
      .trim()
      .toLowerCase();
    const password = this.config.get<string>(
      'FASTSCAN_ADMIN_PASSWORD',
      'admin',
    );
    const role = this.config.get<Role>('FASTSCAN_ADMIN_ROLE', Role.ADMIN);
    await this.user.upsert({
      where: { login },
      update: {},
      create: { login, passwordHash: await hash(password, 12), role },
    });
  }

  async onModuleDestroy() {
    await this.$disconnect();
  }
}
