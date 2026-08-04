import { Controller, Get, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import { Public } from './auth.js';
import { PrismaService } from './prisma.service.js';

type DatabaseCheck = {
  status: 'up' | 'down';
  response_time_ms: number;
};

@ApiTags('Health')
@Controller()
export class HealthController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly config: ConfigService,
  ) {}

  @Public()
  @Get('health')
  @ApiOperation({ summary: 'Detailed API and database health' })
  @ApiResponse({ status: 200, description: 'API and PostgreSQL are ready' })
  @ApiResponse({ status: 503, description: 'PostgreSQL is unavailable' })
  async health() {
    return this.readinessResponse();
  }

  @Public()
  @Get('health/live')
  @ApiOperation({ summary: 'Process liveness probe' })
  @ApiResponse({ status: 200, description: 'API process is running' })
  live() {
    return this.response('ok');
  }

  @Public()
  @Get('health/ready')
  @ApiOperation({ summary: 'API and database readiness probe' })
  @ApiResponse({ status: 200, description: 'API and PostgreSQL are ready' })
  @ApiResponse({ status: 503, description: 'PostgreSQL is unavailable' })
  async ready() {
    return this.readinessResponse();
  }

  private async readinessResponse() {
    const database = await this.databaseCheck();
    const response = this.response(
      database.status === 'up' ? 'ok' : 'error',
      database,
    );
    if (database.status === 'down') {
      throw new ServiceUnavailableException(response);
    }
    return response;
  }

  private response(status: 'ok' | 'error', database?: DatabaseCheck) {
    return {
      status,
      service: 'fastscan-api',
      version: this.config.get<string>('APP_VERSION', '0.4.0'),
      environment: this.config.get<string>('NODE_ENV', 'development'),
      timestamp: new Date().toISOString(),
      uptime_seconds: Math.floor(process.uptime()),
      checks: {
        api: { status: 'up' as const },
        ...(database ? { database } : {}),
      },
    };
  }

  private async databaseCheck(): Promise<DatabaseCheck> {
    const startedAt = Date.now();
    try {
      await this.prisma.$queryRawUnsafe('SELECT 1');
      return {
        status: 'up',
        response_time_ms: Date.now() - startedAt,
      };
    } catch {
      return {
        status: 'down',
        response_time_ms: Date.now() - startedAt,
      };
    }
  }
}
