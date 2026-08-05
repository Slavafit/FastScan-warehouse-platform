export const Role = {
  ADMIN: 'ADMIN',
  WORKER: 'WORKER',
  CLIENT: 'CLIENT',
} as const;

export type Role = (typeof Role)[keyof typeof Role];

export const NotificationType = {
  INFO: 'INFO',
  WELCOME: 'WELCOME',
  ORDER_REGISTERED: 'ORDER_REGISTERED',
  ORDER_PACKED: 'ORDER_PACKED',
  ORDER_UNPACKED: 'ORDER_UNPACKED',
} as const;

export type NotificationType =
  (typeof NotificationType)[keyof typeof NotificationType];

export const DictionaryType = {
  WAREHOUSE: 'WAREHOUSE',
  COUNTRY: 'COUNTRY',
  ORDER_STATUS: 'ORDER_STATUS',
  CURRENCY: 'CURRENCY',
  PACKAGING_TYPE: 'PACKAGING_TYPE',
} as const;

export type DictionaryType =
  (typeof DictionaryType)[keyof typeof DictionaryType];

export const Prisma = {};

export class PrismaClient {}
