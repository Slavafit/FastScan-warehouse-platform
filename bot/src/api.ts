// apps/bot/src/api.ts

import axios, { AxiosInstance } from 'axios';
import { logger } from './logger';

const BASE_URL = process.env.BACKEND_URL ?? 'http://localhost:3000/api/v1';

export interface ApiErrorPayload {
  statusCode?: number;
  code?: string;
  message?: string | string[];
  details?: Record<string, unknown>;
}

export function getApiErrorPayload(err: any): ApiErrorPayload | null {
  return err?.response?.data ?? null;
}

export function getApiErrorMessage(err: any): string {
  const payload = getApiErrorPayload(err);
  if (!payload) return err?.message ?? 'Неизвестная ошибка';

  const details = payload.details ?? {};
  if (payload.code === 'ORDER_TRACK_DUPLICATE' && details.track) {
    return `Трек ${details.track} уже принят. Повторная операция отменена.`;
  }
  if (payload.code === 'ORDER_TRACK_REQUIRED') return 'Введите трек-номер.';
  if (payload.code === 'ORDER_TRACK_LENGTH_INVALID') return 'Трек должен содержать от 8 до 30 символов.';
  if (payload.code === 'ORDER_TRACK_FORMAT_INVALID') {
    return 'Трек может содержать только латинские буквы, цифры и символы «-», «_», «.».';
  }
  if (payload.code === 'HS_CODE_NOT_FOUND') {
    return 'Код ТН ВЭД не найден. Выберите позицию из справочника или укажите данные вручную.';
  }
  if (payload.code === 'ACCESS_DENIED') return 'Недостаточно прав для этого действия.';
  if (payload.code === 'INVOICE_INVALID_TYPE') return 'Можно загрузить только PDF-инвойс.';
  if (payload.code === 'INVOICE_TOO_LARGE') return 'PDF-инвойс должен быть не больше 10 MB.';

  const message = payload.message;
  return Array.isArray(message) ? message.join(', ') : message ?? 'Ошибка API';
}

// Токен менеджера/админа — используется для операций склада
let managerToken           = '';
let storedManagerTelegramId = '';

// ── Базовый клиент (без токена) ───────────────────────────────────────────────

export const api: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10_000,
});

// Подставляем токен менеджера по умолчанию
api.interceptors.request.use(config => {
  if (managerToken && !config.headers['Authorization']) {
    config.headers['Authorization'] = `Bearer ${managerToken}`;
  }
  return config;
});

// При 401 — обновляем токен и повторяем
api.interceptors.response.use(
  r => r,
  async err => {
    if (err.response?.status === 401 && storedManagerTelegramId) {
      await refreshToken(storedManagerTelegramId);
      err.config.headers['Authorization'] = `Bearer ${managerToken}`;
      return api.request(err.config);
    }
    logger.debug('[API Error]', err.config?.url, err.response?.status, err.response?.data);

    return Promise.reject(err);
  },
);

// ── Клиент с конкретным токеном пользователя ──────────────────────────────────

export function userApi(token: string): AxiosInstance {
  return axios.create({
    baseURL: BASE_URL,
    headers: {
      'Content-Type':  'application/json',
      'Authorization': `Bearer ${token}`,
    },
    timeout: 10_000,
  });
}

// ── Авторизация ───────────────────────────────────────────────────────────────

/**
 * Авторизация через Telegram.
 * Возвращает { token, user } и сохраняет токен если это менеджер/админ.
 */
export async function authViaTelegram(telegramId: string): Promise<{
  token: string;
  user:  any;
} | null> {
  const botSecret = process.env.TELEGRAM_BOT_TOKEN?.trim() ?? '';
  if (!botSecret) return null;

  try {
    const res = await axios.post(`${BASE_URL}/auth/bot`, { telegramId, botSecret });
    if (res.data?.needsRegistration) return null;

    const { token, user } = res.data;

    // Если менеджер или админ — сохраняем как рабочий токен
    if (user.role === 'manager' || user.role === 'admin') {
      managerToken            = token;
      storedManagerTelegramId = telegramId;
    }

    return { token, user };
  } catch {
    return null;
  }
}

/**
 * Регистрация нового клиента через бота.
 */
export async function registerClient(params: {
  telegramId: string;
  name:       string;
  lastName?:  string | null;
  username:   string | null;
  email?:     string;
  phone?:     string | null;
  address?: {
    label:      string;
    recipient:  string;
    phone:      string;
    country:    string;
    city:       string;
    region?:    string;
    postalCode: string;
    street:     string;
    isDefault?: boolean;
  };
}): Promise<{ token: string; user: any } | null> {
  const botSecret = process.env.TELEGRAM_BOT_TOKEN?.trim() ?? '';
  try {
    const res = await axios.post(`${BASE_URL}/auth/bot/register`, {
      ...params,
      botSecret,
    });
    return res.data;
  } catch {
    return null;
  }
}

/**
 * Обновление токена менеджера (при истечении).
 */
export async function refreshToken(telegramId?: string): Promise<void> {
  const id = telegramId ?? storedManagerTelegramId;
  if (!id) return;
  const result = await authViaTelegram(id);
  if (result) {
    logger.debug('[Bot] Token refreshed for:', result.user.role);
  }
}

// ── API методы (работают с токеном менеджера) ─────────────────────────────────

export const getInboundCarriers  = () =>
  api.get('/carriers/inbound').then(r => r.data);

export const getSenders          = () =>
  api.get('/senders').then(r => r.data);

export const getSender           = (senderId: string) =>
  api.get(`/senders/${senderId}`).then(r => r.data);

export const createSender        = (name: string) =>
  api.post('/senders', { name }).then(r => r.data);

export const seedSenders         = () =>
  api.post('/senders/seed').then(r => r.data);

export const getOutboundCarriers = (country?: string) =>
  api.get('/carriers/outbound', { params: { country } }).then(r => r.data);

export const findOrderByTrack    = (track: string) =>
  api.get(`/orders/track/${encodeURIComponent(track)}`).then(r => r.data);

export const getOrderById        = (orderId: string) =>
  api.get(`/orders/${orderId}`).then(r => r.data);

export const findParcelByTrack   = (track: string) =>
  api.get(`/parcels/track/${encodeURIComponent(track)}`).then(r => r.data);

export const findShipmentByTrack = (track: string) =>
  api.get(`/shipments/track/${encodeURIComponent(track)}`).then(r => r.data);

export const getShipmentTrackSummary = (boxId: string) =>
  api.get(`/shipments/box/${boxId}/summary`).then(r => r.data);

export const getClientOrders     = (clientId: string) =>
  api.get(`/orders/client/${clientId}`).then(r => r.data);

export const createOrder = (data: {
  clientId:           string;
  inTrack:            string;
  incomingCarrierId?: string;
  senderName?:        string;
  weight:             number;
  itemNameRu?:        string;
  declaredValue?:     number;
}) => api.post('/orders', data).then(r => r.data);

export const uploadPhoto = async (
  orderId:  string,
  buffer:   Buffer,
  mimeType: string,
) => {
  const FormData = require('form-data');
  const form     = new FormData();
  form.append('photos', buffer, { filename: 'photo.jpg', contentType: mimeType });
  return api.post(`/orders/${orderId}/photos`, form, {
    headers: { ...form.getHeaders() },
  }).then(r => r.data);
};

export const getParcels          = (status?: string) =>
  api.get('/parcels', { params: { status } }).then(r => r.data);

export const createParcel        = (clientId: string) =>
  api.post('/parcels', { clientId }).then(r => r.data);

export const addOrderToParcel    = (parcelId: string, orderIds: string[]) =>
  api.post(`/parcels/${parcelId}/orders`, { orderIds }).then(r => r.data);

export const finalizeParcel      = (parcelId: string, data: any) =>
  api.patch(`/parcels/${parcelId}/finalize`, data).then(r => r.data);

export const getBoxes            = (status?: string) =>
  api.get('/boxes', { params: { status } }).then(r => r.data);

export const getBox              = (boxId: string) =>
  api.get(`/boxes/${boxId}`).then(r => r.data);

export const createBox           = () =>
  api.post('/boxes', {}).then(r => r.data);

export const addToBox            = (boxId: string, data: any) =>
  api.post(`/boxes/${boxId}/items`, data).then(r => r.data);

export const finalizeBox         = (boxId: string, data: any) =>
  api.patch(`/boxes/${boxId}/finalize`, data).then(r => r.data);

export const getBoxContents      = (boxId: string) =>
  api.get(`/boxes/${boxId}/contents`).then(r => r.data);

export const getClients          = () =>
  api.get('/users').then(r => r.data);

export const getClient           = (clientId: string) =>
  api.get(`/users/${clientId}`).then(r => r.data);

export const updateClient        = (clientId: string, data: any) =>
  api.patch(`/users/${clientId}`, data).then(r => r.data);

export const getClientAddresses  = (clientId: string) =>
  api.get(`/addresses/client/${clientId}`).then(r => r.data);

export const getRatesForBox      = (boxId: string) =>
  api.post(`/rates/box/${boxId}`).then(r => r.data);

export const shipBox             = (boxId: string, data: any) =>
  api.post(`/rates/box/${boxId}/ship`, data).then(r => r.data);

export const addNote             = (data: any) =>
  api.post('/notes', data).then(r => r.data);

export const getNotes            = (entityType: string, entityId: string) =>
  api.get('/notes', { params: { entityType, entityId } }).then(r => r.data);

export const getStatusHistory    = (entityType: string, entityId: string) =>
  api.get(`/status-history/${entityType}/${entityId}`).then(r => r.data);

export const searchHsCodes       = (query: string) =>
  api.get('/orders/hs-codes', { params: { q: query } }).then(r => r.data);

export const getOrderItems       = (orderId: string) =>
  api.get(`/orders/${orderId}/items`).then(r => r.data);

export const addOrderItem        = (orderId: string, data: any) =>
  api.post(`/orders/${orderId}/items`, data).then(r => r.data);

export const updateOrderItem     = (orderId: string, itemId: string, data: any) =>
  api.patch(`/orders/${orderId}/items/${itemId}`, data).then(r => r.data);

export const deleteOrderItem     = (orderId: string, itemId: string) =>
  api.post(`/orders/${orderId}/items/${itemId}/delete`).then(r => r.data);

// ── Клиентские API методы (с токеном клиента) ─────────────────────────────────

export const getMyOrders         = (token: string) =>
  userApi(token).get('/orders/my').then(r => r.data);

export const getMyOrderById      = (token: string, orderId: string) =>
  userApi(token).get(`/orders/${orderId}`).then(r => r.data);

export const getMyParcels        = (token: string) =>
  userApi(token).get('/parcels/my').then(r => r.data);

export const getMyAddresses      = (token: string) =>
  userApi(token).get('/addresses/my').then(r => r.data);

export const createMyAddress     = (token: string, data: any) =>
  userApi(token).post('/addresses', data).then(r => r.data);

export const updateMyAddress     = (token: string, addressId: string, data: any) =>
  userApi(token).patch(`/addresses/${addressId}`, data).then(r => r.data);

export const getMyShipmentTrackSummary = (token: string, boxId: string) =>
  userApi(token).get(`/shipments/box/${boxId}/summary`).then(r => r.data);

export const getMyNotes          = (token: string, entityType: string, entityId: string) =>
  userApi(token).get('/notes', { params: { entityType, entityId } }).then(r => r.data);

export const addMyNote           = (token: string, data: any) =>
  userApi(token).post('/notes', data).then(r => r.data);

export const searchMyHsCodes     = (token: string, query: string) =>
  userApi(token).get('/orders/hs-codes', { params: { q: query } }).then(r => r.data);

export const getMyOrderItems     = (token: string, orderId: string) =>
  userApi(token).get(`/orders/${orderId}/items`).then(r => r.data);

export const addMyOrderItem      = (token: string, orderId: string, data: any) =>
  userApi(token).post(`/orders/${orderId}/items`, data).then(r => r.data);

export const updateMyOrderItem   = (token: string, orderId: string, itemId: string, data: any) =>
  userApi(token).patch(`/orders/${orderId}/items/${itemId}`, data).then(r => r.data);

export const deleteMyOrderItem   = (token: string, orderId: string, itemId: string) =>
  userApi(token).post(`/orders/${orderId}/items/${itemId}/delete`).then(r => r.data);
