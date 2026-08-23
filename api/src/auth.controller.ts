import {
  Body,
  ConflictException,
  Controller,
  Post,
  UnauthorizedException,
} from '@nestjs/common';
import { compare, hash } from 'bcryptjs';
import { ClientRegistrationDto, LoginDto, RefreshDto } from './dto.js';
import { NotificationType, Role } from './generated/prisma/client.js';
import { Public, TokenService } from './auth.js';
import { PrismaService } from './prisma.service.js';
import { NotificationService } from './notification.service.js';

@Public()
@Controller()
export class AuthController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly tokens: TokenService,
    private readonly notifications: NotificationService,
  ) {}

  @Post('api/admin/auth/login')
  async warehouseLogin(@Body() dto: LoginDto) {
    return this.login(dto, false);
  }

  @Post('api/mobile/auth/login')
  async mobileLogin(@Body() dto: LoginDto) {
    return this.login(dto, false);
  }

  @Post('api/admin/auth/refresh')
  async warehouseRefresh(@Body() dto: RefreshDto) {
    return this.refresh(dto, false);
  }

  @Post('api/mobile/auth/refresh')
  async mobileRefresh(@Body() dto: RefreshDto) {
    return this.refresh(dto, false);
  }

  @Post('api/client/auth/register')
  async register(@Body() dto: ClientRegistrationDto) {
    const email = dto.email.trim().toLowerCase();
    const receiver = await this.prisma.receiver.findUnique({
      where: { clientCode: dto.client_code.trim().toUpperCase() },
    });
    if (!receiver) throw new UnauthorizedException('Client code not found');
    const existing = await this.prisma.clientAccount.findFirst({
      where: { OR: [{ email }, { receiverId: receiver.id }] },
    });
    if (
      existing ||
      (await this.prisma.user.findUnique({ where: { login: email } }))
    ) {
      throw new ConflictException('Client account already exists');
    }
    const user = await this.prisma.user.create({
      data: {
        login: email,
        passwordHash: await hash(dto.password, 12),
        role: Role.CLIENT,
        client: {
          create: {
            email,
            receiver: { connect: { id: receiver.id } },
          },
        },
      },
      include: { client: true },
    });
    if (user.client) {
      await this.notifications.createForAccount(
        user.client.id,
        NotificationType.WELCOME,
        'Welcome to FastScan',
        'Your client account has been created successfully.',
      );
    }
    return this.tokens.pair(user);
  }

  @Post('api/client/auth/login')
  async clientLogin(@Body() dto: LoginDto) {
    return this.login(dto, true);
  }

  @Post('api/client/auth/refresh')
  async clientRefresh(@Body() dto: RefreshDto) {
    return this.refresh(dto, true);
  }

  private async login(dto: LoginDto, client: boolean) {
    const user = await this.prisma.user.findUnique({
      where: { login: dto.login.trim().toLowerCase() },
    });
    const correctRole = client
      ? user?.role === Role.CLIENT
      : user?.role !== Role.CLIENT;
    if (
      !user ||
      !user.enabled ||
      !correctRole ||
      !(await compare(dto.password, user.passwordHash))
    ) {
      throw new UnauthorizedException();
    }
    return this.tokens.pair(user);
  }

  private async refresh(dto: RefreshDto, client: boolean) {
    const payload = await this.tokens.verifyRefresh(dto.refresh_token);
    const user = await this.prisma.user.findUnique({
      where: { id: payload.sub },
    });
    const correctRole = client
      ? user?.role === Role.CLIENT
      : user?.role !== Role.CLIENT;
    if (!user || !user.enabled || !correctRole)
      throw new UnauthorizedException();
    return this.tokens.pair(user);
  }
}
