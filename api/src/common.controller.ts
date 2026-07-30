import {
  Body,
  Controller,
  Delete,
  Get,
  NotFoundException,
  Param,
  Post,
  Query,
} from '@nestjs/common';
import { randomInt } from 'node:crypto';
import { ReceiverUpsertDto } from './dto.js';
import { Role } from './generated/prisma/client.js';
import { Roles } from './auth.js';
import { PrismaService } from './prisma.service.js';

@Roles(Role.ADMIN, Role.WORKER)
@Controller('api/common')
export class CommonController {
  constructor(private readonly prisma: PrismaService) {}

  @Get('reciever')
  async receiver(@Query('client_code') clientCode: string) {
    const receiver = await this.prisma.receiver.findUnique({
      where: { clientCode: clientCode?.trim().toUpperCase() },
    });
    return receiver ? [this.receiverResponse(receiver)] : [];
  }

  @Get('all-reciever')
  async receivers() {
    const result = await this.prisma.receiver.findMany({
      orderBy: [{ lastName: 'asc' }, { firstName: 'asc' }],
    });
    return result.map((item) => ({
      first_name: item.firstName,
      last_name: item.lastName,
      client_code: item.clientCode,
    }));
  }

  @Post('reciever')
  async upsert(@Body() dto: ReceiverUpsertDto) {
    const explicit = dto.client_code?.trim().toUpperCase();
    const clientCode = explicit || (await this.randomClientCode());
    const receiver = await this.prisma.receiver.upsert({
      where: { clientCode },
      update: {
        firstName: dto.first_name.trim(),
        lastName: dto.last_name.trim(),
        packageShoes: dto.package_shoes,
        bagPackage: dto.bag_package,
      },
      create: {
        clientCode,
        firstName: dto.first_name.trim(),
        lastName: dto.last_name.trim(),
        packageShoes: dto.package_shoes,
        bagPackage: dto.bag_package,
      },
    });
    return {
      ...this.receiverResponse(receiver),
      client_code: receiver.clientCode,
    };
  }

  @Delete('reciever/:guid')
  async remove(@Param('guid') guid: string) {
    const found = await this.prisma.receiver.findUnique({
      where: { id: guid },
    });
    if (!found) throw new NotFoundException('Receiver not found');
    await this.prisma.receiver.delete({ where: { id: guid } });
    return { success: true, message: 'Receiver deleted' };
  }

  private receiverResponse(receiver: {
    id: string;
    firstName: string;
    lastName: string;
    packageShoes: number;
    bagPackage: number;
  }) {
    return {
      full_name: `${receiver.firstName} ${receiver.lastName}`.trim(),
      guid: receiver.id,
      package_shoes: receiver.packageShoes,
      bag_package: receiver.bagPackage,
    };
  }

  private async randomClientCode() {
    const config = await this.prisma.clientCodeConfig.findUnique({
      where: { id: 1 },
    });
    const prefix = config?.codePrefix ?? 'KG';
    for (let attempt = 0; attempt < 100; attempt += 1) {
      const code = `${prefix}${randomInt(100_000, 1_000_000)}`;
      if (
        !(await this.prisma.receiver.findUnique({
          where: { clientCode: code },
        }))
      )
        return code;
    }
    throw new Error('Could not generate a unique client code');
  }
}
