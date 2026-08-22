// apps/bot/src/session.ts
// Хранит временное состояние менеджера в процессе многошагового действия
// (например: приёмка посылки требует нескольких шагов)

export type BotStep =
  | 'idle'
  | 'registration:await_name'
  | 'registration:await_last_name'
  | 'registration:await_email'
  | 'registration:await_phone'
  | 'registration:await_address_country'
  | 'registration:await_address_city'
  | 'registration:await_address_postal_code'
  | 'registration:await_address_street'
  | 'registration:await_address_region'
  | 'client:await_email'
  | 'client:await_name'
  | 'client:await_last_name'
  | 'client_address:await_recipient'
  | 'client_address:await_phone'
  | 'client_address:await_country'
  | 'client_address:await_city'
  | 'client_address:await_postal_code'
  | 'client_address:await_street'
  | 'client_address:await_house_number'
  | 'client_address:await_building'
  | 'client_address:await_apartment'
  | 'client_address:await_region'
  | 'client_order:await_track'
  | 'client_order:await_sender'
  | 'client_order:await_weight'
  | 'client_order:await_dimensions'
  | 'manager_order:await_photo'
  // Приёмка
  | 'receive:await_track'
  | 'receive:await_carrier'
  | 'receive:await_sender'
  | 'receive:await_client'
  | 'receive:await_weight'
  | 'receive:await_item_name'
  | 'receive:await_value'
  | 'receive:await_photo'
  // Parcel
  | 'parcel:await_client'
  | 'parcel:await_orders'
  | 'parcel:await_weight'
  // Box
  | 'box:await_parcel_or_order'
  | 'box:await_weight'
  | 'box:await_dimensions'
  | 'box:await_address'
  // Notes
  | 'note:await_text'
  // Order items / customs description
  | 'order_item:await_keyword'
  | 'order_item:await_manual_hs'
  | 'order_item:await_qty_price'
  | 'order_item:await_edit'
  // Manager
  | 'manager:await_discount'
  // Admin
  | 'admin:await_promote';

export interface BotSession {
  step: BotStep;
  userToken?: string;   // JWT токен этого пользователя
  userRole?:  string;   // роль: admin | manager | client

  registration?: {
    telegramId: string;
    name:       string;
    lastName?:  string | null;
    username:   string | null;
    email?:     string;
    phone?:     string | null;
    address?: Partial<{
      label:      string;
      recipient:  string;
      phone:      string;
      country:    string;
      city:       string;
      region:     string;
      postalCode: string;
      street:     string;
      isDefault:  boolean;
    }>;
  };

  // Данные в процессе приёмки
  receive?: {
    clientId?:         string;
    inTrack?:          string;
    incomingCarrierId?: string;
    senderName?:        string;
    weight?:           number;
    itemNameRu?:       string;
    declaredValue?:    number;
    orderId?:          string;  // ID созданного заказа
  };

  // Данные в процессе сборки Parcel
  parcel?: {
    id?:       string;
    clientId?: string;
    orderIds:  string[];
  };

  // Данные в процессе добавления в Box
  box?: {
    id?:         string;
    weight?:     number;
    dimensions?: {
      length: number;
      width:  number;
      height: number;
    };
  };

  clientProfileDraft?: {
    name?: string;
  };

  clientAddressDraft?: {
    id?:          string;
    label?:       string;
    recipient?:   string;
    phone?:       string;
    country?:     string;
    city?:        string;
    region?:      string;
    postalCode?:  string;
    street?:      string;
    houseNumber?: string;
    building?:    string;
    apartment?:   string;
    isDefault?:   boolean;
  };

  discountEdit?: {
    clientId: string;
    clientName: string;
  };

  noteDraft?: {
    entityType: 'order' | 'parcel' | 'box';
    entityId:   string;
  };

  orderItemDraft?: {
    orderId:       string;
    itemId?:       string;
    keyword?:      string;
    itemNameRu?:   string;
    itemNameEn?:   string;
    hsCode?:       string;
    hsDescription?: string;
  };

  clientOrderDraft?: {
    inTrack?: string;
    senderName?: string;
    weight?: number;
  };

  managerOrderPhoto?: {
    orderId: string;
    track: string;
  };
}

// Хранилище сессий в памяти (ключ — telegramId пользователя)
const sessions = new Map<number, BotSession>();

export function getSession(telegramId: number): BotSession {
  if (!sessions.has(telegramId)) {
    sessions.set(telegramId, { step: 'idle' });
  }
  return sessions.get(telegramId)!;
}

export function setSession(telegramId: number, session: Partial<BotSession>): void {
  const current = getSession(telegramId);
  sessions.set(telegramId, { ...current, ...session });
}

export function clearSession(telegramId: number, preserveAuth = true): void {
  const current = getSession(telegramId);
  sessions.set(telegramId, {
    step: 'idle',
    ...(preserveAuth ? {
      userToken: current.userToken,
      userRole:  current.userRole,
    } : {}),
  });
}
