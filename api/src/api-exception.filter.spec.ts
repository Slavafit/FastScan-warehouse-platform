import {
  BadRequestException,
  ForbiddenException,
  HttpStatus,
} from '@nestjs/common';
import { describeApiException } from './api-exception.filter.js';

describe('Global API error descriptions', () => {
  it('returns field violations for validation errors', () => {
    expect(
      describeApiException(
        new BadRequestException({
          statusCode: 400,
          error: 'Bad Request',
          message: [
            'tracking_number should not be empty',
            'declared_value must not be less than 0',
          ],
        }),
      ),
    ).toEqual({
      statusCode: 400,
      error: 'Bad Request',
      code: 'VALIDATION_ERROR',
      message: 'Проверьте данные запроса',
      details: {
        violations: [
          'tracking_number should not be empty',
          'declared_value must not be less than 0',
        ],
      },
    });
  });

  it('uses a clear default for generated authorization errors', () => {
    expect(describeApiException(new ForbiddenException())).toMatchObject({
      statusCode: HttpStatus.FORBIDDEN,
      code: 'FORBIDDEN',
      message: 'Недостаточно прав для выполнения операции',
    });
  });

  it('maps common Prisma conflicts without exposing database details', () => {
    expect(
      describeApiException({
        name: 'PrismaClientKnownRequestError',
        code: 'P2002',
        clientVersion: '7.9.1',
        meta: { target: ['tracking_number'] },
        message: 'Raw SQL details must not be returned',
      }),
    ).toEqual({
      statusCode: HttpStatus.CONFLICT,
      error: 'Conflict',
      code: 'DUPLICATE_RECORD',
      message: 'Запись с такими данными уже существует',
      details: { fields: ['tracking_number'] },
    });
  });

  it('hides unexpected exception details', () => {
    const result = describeApiException(
      new Error('password=secret and raw database query'),
    );
    expect(result).toMatchObject({
      statusCode: HttpStatus.INTERNAL_SERVER_ERROR,
      code: 'INTERNAL_SERVER_ERROR',
    });
    expect(JSON.stringify(result)).not.toContain('secret');
    expect(JSON.stringify(result)).not.toContain('database query');
  });
});
