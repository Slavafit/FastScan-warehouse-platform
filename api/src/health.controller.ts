import { Controller, Get } from '@nestjs/common';
import { Public } from './auth.js';

@Controller()
export class HealthController {
  @Public()
  @Get('health')
  health() {
    return {
      status: 'ok',
      service: 'fastscan-api',
      timestamp: new Date().toISOString(),
    };
  }
}
