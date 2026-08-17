import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { randomUUID } from 'node:crypto';

export interface ApiErrorDescription {
  statusCode: number;
  error: string;
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

const STATUS_LABELS: Record<number, string> = {
  [HttpStatus.BAD_REQUEST]: 'Bad Request',
  [HttpStatus.UNAUTHORIZED]: 'Unauthorized',
  [HttpStatus.FORBIDDEN]: 'Forbidden',
  [HttpStatus.NOT_FOUND]: 'Not Found',
  [HttpStatus.CONFLICT]: 'Conflict',
  [HttpStatus.UNPROCESSABLE_ENTITY]: 'Unprocessable Entity',
  [HttpStatus.TOO_MANY_REQUESTS]: 'Too Many Requests',
  [HttpStatus.INTERNAL_SERVER_ERROR]: 'Internal Server Error',
  [HttpStatus.SERVICE_UNAVAILABLE]: 'Service Unavailable',
};

const STATUS_CODES: Record<number, string> = {
  [HttpStatus.BAD_REQUEST]: 'BAD_REQUEST',
  [HttpStatus.UNAUTHORIZED]: 'UNAUTHORIZED',
  [HttpStatus.FORBIDDEN]: 'FORBIDDEN',
  [HttpStatus.NOT_FOUND]: 'NOT_FOUND',
  [HttpStatus.CONFLICT]: 'CONFLICT',
  [HttpStatus.UNPROCESSABLE_ENTITY]: 'UNPROCESSABLE_ENTITY',
  [HttpStatus.TOO_MANY_REQUESTS]: 'TOO_MANY_REQUESTS',
  [HttpStatus.INTERNAL_SERVER_ERROR]: 'INTERNAL_SERVER_ERROR',
  [HttpStatus.SERVICE_UNAVAILABLE]: 'SERVICE_UNAVAILABLE',
};

const DEFAULT_MESSAGES: Record<number, string> = {
  [HttpStatus.BAD_REQUEST]: 'Проверьте данные запроса',
  [HttpStatus.UNAUTHORIZED]: 'Требуется авторизация',
  [HttpStatus.FORBIDDEN]: 'Недостаточно прав для выполнения операции',
  [HttpStatus.NOT_FOUND]: 'Запрашиваемый ресурс не найден',
  [HttpStatus.CONFLICT]: 'Операция конфликтует с существующими данными',
  [HttpStatus.UNPROCESSABLE_ENTITY]: 'Запрос не может быть обработан',
  [HttpStatus.TOO_MANY_REQUESTS]: 'Слишком много запросов. Повторите позже',
  [HttpStatus.INTERNAL_SERVER_ERROR]:
    'Внутренняя ошибка сервера. Сообщите код запроса администратору',
  [HttpStatus.SERVICE_UNAVAILABLE]:
    'Сервис временно недоступен. Повторите попытку позже',
};

@Catch()
export class ApiExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(ApiExceptionFilter.name);

  catch(exception: unknown, host: ArgumentsHost) {
    const context = host.switchToHttp();
    const response = context.getResponse<Response>();
    const request = context.getRequest<Request>();
    const description = describeApiException(exception);
    const requestId = this.requestId(request.headers['x-request-id']);

    response.setHeader('X-Request-Id', requestId);
    response.status(description.statusCode).json({
      statusCode: description.statusCode,
      error: description.error,
      code: description.code,
      message: description.message,
      ...(description.details ? { details: description.details } : {}),
      path: request.originalUrl || request.url,
      method: request.method,
      timestamp: new Date().toISOString(),
      request_id: requestId,
    });

    if (description.statusCode >= 500) {
      const stack = exception instanceof Error ? exception.stack : undefined;
      this.logger.error(
        `${request.method} ${request.originalUrl || request.url} ${description.code} request_id=${requestId}`,
        stack,
      );
    }
  }

  private requestId(header: string | string[] | undefined) {
    const value = Array.isArray(header) ? header[0] : header;
    const normalized = value?.trim();
    return normalized && normalized.length <= 100 ? normalized : randomUUID();
  }
}

export function describeApiException(exception: unknown): ApiErrorDescription {
  if (exception instanceof HttpException)
    return describeHttpException(exception);

  const prisma = describePrismaException(exception);
  if (prisma) return prisma;

  return {
    statusCode: HttpStatus.INTERNAL_SERVER_ERROR,
    error: STATUS_LABELS[HttpStatus.INTERNAL_SERVER_ERROR],
    code: STATUS_CODES[HttpStatus.INTERNAL_SERVER_ERROR],
    message: DEFAULT_MESSAGES[HttpStatus.INTERNAL_SERVER_ERROR],
  };
}

function describeHttpException(exception: HttpException): ApiErrorDescription {
  const statusCode = exception.getStatus();
  const response = exception.getResponse();
  const body = isRecord(response) ? response : undefined;
  const rawMessage =
    body?.message ?? (typeof response === 'string' ? response : undefined);
  const violations = Array.isArray(rawMessage)
    ? rawMessage.filter((item): item is string => typeof item === 'string')
    : undefined;
  const code =
    typeof body?.code === 'string'
      ? body.code
      : violations?.length
        ? 'VALIDATION_ERROR'
        : (STATUS_CODES[statusCode] ?? `HTTP_${statusCode}`);
  const explicitMessage =
    typeof rawMessage === 'string' ? rawMessage.trim() : '';
  const generatedMessage =
    !explicitMessage ||
    ['Unauthorized', 'Forbidden', 'Forbidden resource', 'Not Found'].includes(
      explicitMessage,
    ) ||
    /^Cannot (GET|POST|PUT|PATCH|DELETE)\b/.test(explicitMessage);
  const extraResponse =
    body && rawMessage === undefined
      ? withoutKeys(body, ['statusCode', 'error', 'code'])
      : undefined;

  return {
    statusCode,
    error:
      typeof body?.error === 'string'
        ? body.error
        : (STATUS_LABELS[statusCode] ?? 'Request Error'),
    code,
    message: violations?.length
      ? DEFAULT_MESSAGES[HttpStatus.BAD_REQUEST]
      : generatedMessage
        ? (DEFAULT_MESSAGES[statusCode] ?? 'Не удалось выполнить запрос')
        : explicitMessage,
    ...(violations?.length
      ? { details: { violations } }
      : extraResponse && Object.keys(extraResponse).length
        ? { details: { response: extraResponse } }
        : {}),
  };
}

function describePrismaException(
  exception: unknown,
): ApiErrorDescription | undefined {
  if (!isRecord(exception)) return undefined;
  const code = typeof exception.code === 'string' ? exception.code : '';
  const name = typeof exception.name === 'string' ? exception.name : '';
  const isPrisma =
    /^P\d{4}$/.test(code) &&
    (typeof exception.clientVersion === 'string' || name.includes('Prisma'));
  if (!isPrisma) return undefined;

  const meta = isRecord(exception.meta) ? exception.meta : undefined;
  const target = Array.isArray(meta?.target)
    ? meta.target.filter((item): item is string => typeof item === 'string')
    : undefined;
  if (code === 'P2002') {
    return {
      statusCode: HttpStatus.CONFLICT,
      error: STATUS_LABELS[HttpStatus.CONFLICT],
      code: 'DUPLICATE_RECORD',
      message: 'Запись с такими данными уже существует',
      ...(target?.length ? { details: { fields: target } } : {}),
    };
  }
  if (code === 'P2025') {
    return {
      statusCode: HttpStatus.NOT_FOUND,
      error: STATUS_LABELS[HttpStatus.NOT_FOUND],
      code: 'RECORD_NOT_FOUND',
      message: 'Запись не найдена',
    };
  }
  if (code === 'P2003' || code === 'P2014') {
    return {
      statusCode: HttpStatus.CONFLICT,
      error: STATUS_LABELS[HttpStatus.CONFLICT],
      code: 'RELATED_RECORD_CONFLICT',
      message: 'Операция невозможна: запись используется в других данных',
    };
  }
  if (code === 'P2000') {
    return {
      statusCode: HttpStatus.BAD_REQUEST,
      error: STATUS_LABELS[HttpStatus.BAD_REQUEST],
      code: 'VALUE_TOO_LONG',
      message: 'Значение одного из полей слишком длинное',
    };
  }
  if (code === 'P2023') {
    return {
      statusCode: HttpStatus.BAD_REQUEST,
      error: STATUS_LABELS[HttpStatus.BAD_REQUEST],
      code: 'INVALID_IDENTIFIER',
      message: 'Передан некорректный идентификатор',
    };
  }
  if (code.startsWith('P10') || code === 'P2024') {
    return {
      statusCode: HttpStatus.SERVICE_UNAVAILABLE,
      error: STATUS_LABELS[HttpStatus.SERVICE_UNAVAILABLE],
      code: 'DATABASE_UNAVAILABLE',
      message: 'База данных временно недоступна',
    };
  }
  return {
    statusCode: HttpStatus.INTERNAL_SERVER_ERROR,
    error: STATUS_LABELS[HttpStatus.INTERNAL_SERVER_ERROR],
    code: 'DATABASE_ERROR',
    message: DEFAULT_MESSAGES[HttpStatus.INTERNAL_SERVER_ERROR],
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function withoutKeys(
  value: Record<string, unknown>,
  keys: string[],
): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(value).filter(([key]) => !keys.includes(key)),
  );
}
