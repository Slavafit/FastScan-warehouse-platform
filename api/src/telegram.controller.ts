import {
  Body,
  Controller,
  Get,
  Headers,
  Post,
  UnauthorizedException,
} from '@nestjs/common';
import { Public } from './auth.js';
import { TelegramService } from './telegram.service.js';
import { TelegramClientAccessDto } from './dto.js';

@Public()
@Controller('api/integrations/telegram')
export class TelegramController {
  constructor(private readonly telegram: TelegramService) {}

  @Get('info')
  info() {
    return this.telegram.info();
  }

  @Post('webhook')
  async webhook(
    @Headers('x-telegram-bot-api-secret-token') secret: string | undefined,
    @Body()
    update: { message?: Parameters<TelegramService['handleMessage']>[0] },
  ) {
    if (!this.telegram.webhookSecretIsValid(secret))
      throw new UnauthorizedException();
    await this.telegram.handleMessage(update.message);
    return { success: true };
  }

  @Post('client-access')
  async clientAccess(
    @Headers('x-fastscan-bot-secret') secret: string | undefined,
    @Body() dto: TelegramClientAccessDto,
  ) {
    if (!this.telegram.botApiSecretIsValid(secret))
      throw new UnauthorizedException('Invalid bot credentials');
    return this.telegram.createClientAccess(dto);
  }
}
