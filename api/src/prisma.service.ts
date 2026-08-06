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
      create: {
        id: 1,
        codePrefix: 'KG',
        warehouseName: 'BMG',
        country: 'España',
      },
    });
    await this.seedTestUsers();
  }

  private async seedTestUsers() {
    for (const user of TEST_USERS.filter((item) => item.role !== Role.CLIENT)) {
      await this.user.upsert({
        where: { login: user.login },
        update: {
          passwordHash: await hash(user.password, 12),
          role: user.role,
          enabled: true,
        },
        create: {
          login: user.login,
          passwordHash: await hash(user.password, 12),
          role: user.role,
          enabled: true,
        },
      });
    }

    const client = TEST_USERS.find((item) => item.role === Role.CLIENT)!;
    const receiver = await this.receiver.upsert({
      where: { clientCode: TEST_CLIENT_CODE },
      update: {
        firstName: 'Test',
        lastName: 'Client',
      },
      create: {
        clientCode: TEST_CLIENT_CODE,
        firstName: 'Test',
        lastName: 'Client',
      },
    });
    const clientUser = await this.user.upsert({
      where: { login: client.login },
      update: {
        passwordHash: await hash(client.password, 12),
        role: Role.CLIENT,
        enabled: true,
      },
      create: {
        login: client.login,
        passwordHash: await hash(client.password, 12),
        role: Role.CLIENT,
        enabled: true,
      },
    });

    await this.clientAccount.upsert({
      where: { email: client.login },
      update: {
        userId: clientUser.id,
        receiverId: receiver.id,
        phone: '+34 600 000 000',
        address: 'Test warehouse client',
      },
      create: {
        email: client.login,
        userId: clientUser.id,
        receiverId: receiver.id,
        phone: '+34 600 000 000',
        address: 'Test warehouse client',
      },
    });
  }

  async onModuleDestroy() {
    await this.$disconnect();
  }
}

const TEST_PASSWORD = 'change-me';
const TEST_CLIENT_CODE = 'KGTEST';
const TEST_USERS = [
  { login: 'admin', password: TEST_PASSWORD, role: Role.ADMIN },
  { login: 'worker', password: TEST_PASSWORD, role: Role.WORKER },
  { login: 'client@test.com', password: TEST_PASSWORD, role: Role.CLIENT },
];
