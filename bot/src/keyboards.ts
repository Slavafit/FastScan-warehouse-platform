// apps/bot/src/keyboards.ts
import { InlineKeyboardMarkup } from 'telegraf/types';
import { ReplyKeyboardMarkup } from 'telegraf/types';
import { userFullName } from './user-name';

//Reply Keyboard (постоянная клавиатура внизу экрана)

export const adminReplyMenu = (): ReplyKeyboardMarkup => ({
  keyboard: [
    ['👥 Пользователи', '📊 Статистика'],
    ['➕ Назначить менеджера', '➖ Сделать клиентом'],
    ['🔧 Меню менеджера'],
  ],
  resize_keyboard: true,
  is_persistent: false,
  one_time_keyboard: true,
});

export const managerReplyMenu = (): ReplyKeyboardMarkup => ({
  keyboard: [
    ['📦 Приемка', '📋 Заказы'],
    ['👤 Клиенты', '📬 Отправления'],
    ['🗃 Боксы', '💸 Скидки'],
    ['📊 Статистика'],
    ['👑 Админ меню'],
  ],
  resize_keyboard: true,
  is_persistent: false,
  one_time_keyboard: true,
});

export const clientReplyMenu = (): ReplyKeyboardMarkup => ({
  keyboard: [
    ['➕ Новый заказ', '📦 Мои заказы'],
    ['📬 Отправления'],
    ['📍 Адреса', '📊 Аналитика'],
    ['💸 Финансы', '👤 Профиль'],
  ],
  resize_keyboard: true,
  is_persistent: false,
  one_time_keyboard: true,
});


// ── Главные меню ──────────────────────────────────────────────────────────────

export const adminMenu = (): InlineKeyboardMarkup => ({
  inline_keyboard: [
    [{ text: '👥 Пользователи',         callback_data: 'admin:users' }],
    [{ text: '➕ Назначить менеджера',   callback_data: 'admin:promote' }],
    [{ text: '➖ Сделать клиентом',       callback_data: 'admin:demote' }],
    [{ text: '📊 Статистика',            callback_data: 'admin:stats' }],
    [{ text: '🔧 Меню менеджера',        callback_data: 'switch:manager' }],
  ],
});

export const managerMenu = (): InlineKeyboardMarkup => ({
  inline_keyboard: [
    [
      { text: '📦 Приемка', callback_data: 'receive' },
      { text: '📋 Заказы', callback_data: 'manager:orders' },
    ],
    [{ text: '👤 Клиенты',               callback_data: 'manager:clients' }],
    [
      { text: '📬 Новое отправление',    callback_data: 'parcel_new' },
      { text: '📋 Отправления',          callback_data: 'parcel_list' },
    ],
    [
      { text: '🗃 Новый бокс',           callback_data: 'box_new' },
      { text: '📦 Боксы',                callback_data: 'box_list' },
    ],
    [
      { text: '💸 Скидки',               callback_data: 'discount:list' },
      { text: '📊 Статистика',           callback_data: 'admin:stats' },
    ],
  ],
});

export const clientMenu = (): InlineKeyboardMarkup => ({
  inline_keyboard: [
    [{ text: '➕ Новый заказ',            callback_data: 'client_order:new' }],
    [{ text: '👤 Мой профиль',           callback_data: 'client_menu:profile' }],
    [{ text: '📦 Мои заказы',            callback_data: 'client_menu:orders' }],
    [{ text: '📬 Мои отправления',       callback_data: 'client_menu:parcels' }],
    [{ text: '📍 Мои адреса',            callback_data: 'client_menu:addresses' }],
    [{ text: '📊 Аналитика',             callback_data: 'client_menu:analytics' }],
    [{ text: '💸 Финансы',               callback_data: 'client_menu:finance' }],
  ],
});

export const sessionExpiredMenu = (): InlineKeyboardMarkup => ({
  inline_keyboard: [[
    { text: '▶️ Продолжить работу', callback_data: 'session:restart' },
  ]],
});

// Алиас — используется в хендлерах
export const mainMenu = managerMenu;

// ── Вес — получить с весов или ввести вручную ─────────────────────────────────

export const weightKeyboard = (): InlineKeyboardMarkup => ({
  inline_keyboard: [
    [{ text: '⚖️ Получить с весов', callback_data: 'weight:scale' }],
    [{ text: '✏️ Ввести вручную',   callback_data: 'weight:manual' }],
    [{ text: '❌ Отмена',            callback_data: 'cancel' }],
  ],
});

export const senderKeyboard = (
  senders: Array<{ id: string; name: string }> = [],
): InlineKeyboardMarkup => {
  const items = senders.length
    ? senders
    : [
      { id: 'Zara', name: 'Zara' },
      { id: 'Massimo Dutti', name: 'Massimo Dutti' },
      { id: 'Личные вещи', name: 'Личные вещи' },
    ];

  return {
    inline_keyboard: [
      ...items.map(sender => ([{
        text: sender.name,
        callback_data: `sender:${sender.id}`,
      }])),
      [{ text: '✏️ Ввести вручную', callback_data: 'sender:manual' }],
      [{ text: '❌ Отмена', callback_data: 'cancel' }],
    ],
  };
};

export const clientSenderKeyboard = (
  senders: Array<{ id: string; name: string }>,
): InlineKeyboardMarkup => ({
  inline_keyboard: [
    ...Array.from({ length: Math.ceil(senders.length / 2) }, (_, rowIndex) =>
      senders.slice(rowIndex * 2, rowIndex * 2 + 2).map(sender => ({
        text: sender.name,
        callback_data: `client_sender:${sender.id}`,
      })),
    ),
    [{ text: '❌ Отмена', callback_data: 'cancel' }],
  ],
});

export const clientOrderSkipKeyboard = (
  field: 'weight' | 'dimensions',
): InlineKeyboardMarkup => ({
  inline_keyboard: [[
    {
      text: '⏭ Пропустить',
      callback_data: `client_order:skip_${field}`,
    },
  ]],
});

// ── Стоимость — предустановленные варианты ────────────────────────────────────

export const valueKeyboard = (): InlineKeyboardMarkup => ({
  inline_keyboard: [
    [
      { text: '€20',   callback_data: 'value:20' },
      { text: '€50',   callback_data: 'value:50' },
      { text: '€100',  callback_data: 'value:100' },
    ],
    [
      { text: '€150',  callback_data: 'value:150' },
      { text: '€200',  callback_data: 'value:200' },
      { text: '€500',  callback_data: 'value:500' },
    ],
    [{ text: '✏️ Ввести вручную', callback_data: 'value:manual' }],
    [{ text: '⏭ Пропустить',      callback_data: 'skip' }],
    [{ text: '❌ Отмена',          callback_data: 'cancel' }],
  ],
});

// ── Название товара — популярные категории ────────────────────────────────────

export const itemCategoryKeyboard = (): InlineKeyboardMarkup => ({
  inline_keyboard: [
    [
      { text: '👗 Одежда',     callback_data: 'item:Одежда' },
      { text: '👟 Обувь',      callback_data: 'item:Обувь' },
    ],
    [
      { text: '👜 Сумка',      callback_data: 'item:Сумка' },
      { text: '💄 Косметика',  callback_data: 'item:Косметика' },
    ],
    [
      { text: '📱 Электроника', callback_data: 'item:Электроника' },
      { text: '🧸 Игрушки',    callback_data: 'item:Игрушки' },
    ],
    [{ text: '✏️ Ввести вручную', callback_data: 'item:manual' }],
    [{ text: '⏭ Пропустить',      callback_data: 'skip' }],
    [{ text: '❌ Отмена',          callback_data: 'cancel' }],
  ],
});

// ── Перевозчики ────────────────────────────────────────────────────────────────

export const carriersKeyboard = (
  carriers: Array<{ id: string; name: string }>,
  prefix = 'carrier',
): InlineKeyboardMarkup => ({
  inline_keyboard: [
    ...carriers.map(c => ([{
      text:          c.name,
      callback_data: `${prefix}:${c.id}`,
    }])),
    [{ text: '❌ Отмена', callback_data: 'cancel' }],
  ],
});

// ── Клиенты ───────────────────────────────────────────────────────────────────

export const clientsKeyboard = (
  clients: Array<{ id: string; name: string; lastName?: string | null }>,
): InlineKeyboardMarkup => ({
  inline_keyboard: [
    ...clients.map(c => ([{
      text:          `👤 ${userFullName(c)}`,
      callback_data: `client:${c.id}`,
    }])),
    [{ text: '❌ Отмена', callback_data: 'cancel' }],
  ],
});

export const clientsForDiscountKeyboard = (
  clients: Array<{ id: string; name: string; lastName?: string | null; discount?: number }>,
): InlineKeyboardMarkup => ({
  inline_keyboard: [
    ...clients.map(c => ([{
      text:          `👤 ${userFullName(c)} · скидка ${c.discount ?? 0}%`,
      callback_data: `discount_client:${c.id}`,
    }])),
    [{ text: '❌ Отмена', callback_data: 'cancel' }],
  ],
});

export const clientsForManagerKeyboard = (
  clients: Array<{ id: string; name: string; lastName?: string | null; discount?: number }>,
): InlineKeyboardMarkup => ({
  inline_keyboard: [
    ...clients.map(c => ([{
      text:          `👤 ${userFullName(c)} · ${c.discount ?? 0}%`,
      callback_data: `manager_client:${c.id}`,
    }])),
    [{ text: '❌ Отмена', callback_data: 'cancel' }],
  ],
});

// ── Parcels / Boxes ───────────────────────────────────────────────────────────

export const parcelsKeyboard = (
  parcels: Array<{ id: string; clientId: string }>,
): InlineKeyboardMarkup => ({
  inline_keyboard: [
    ...parcels.map(p => ([{
      text:          `📬 Отправление #${p.id.slice(-6)}`,
      callback_data: `parcel:${p.id}`,
    }])),
    [{ text: '➕ Создать новое', callback_data: 'parcel_new' }],
    [{ text: '❌ Отмена',        callback_data: 'cancel' }],
  ],
});

export const boxesKeyboard = (
  boxes: Array<{ id: string }>,
): InlineKeyboardMarkup => ({
  inline_keyboard: [
    ...boxes.map(b => ([{
      text:          `🗃 Бокс #${b.id.slice(-6)}`,
      callback_data: `box:${b.id}`,
    }])),
    [{ text: '➕ Создать новый', callback_data: 'box_new' }],
    [{ text: '❌ Отмена',        callback_data: 'cancel' }],
  ],
});

// ── Тарифы ────────────────────────────────────────────────────────────────────

export const ratesKeyboard = (
  rates: Array<{
    carrierId:   string;
    carrierName: string;
    price:       number;
    days:        number;
    serviceCode: string;
  }>,
): InlineKeyboardMarkup => ({
  inline_keyboard: [
    ...rates.map(r => ([{
      text:          `${r.carrierName} — €${r.price} (${r.days} дн.)`,
      callback_data: `rate:${r.serviceCode}:${r.carrierId}:${r.price}`,
    }])),
    [{ text: '❌ Отмена', callback_data: 'cancel' }],
  ],
});

// ── Утилиты ───────────────────────────────────────────────────────────────────

export const skipKeyboard = (): InlineKeyboardMarkup => ({
  inline_keyboard: [[
    { text: '⏭ Пропустить', callback_data: 'skip' },
    { text: '❌ Отмена',     callback_data: 'cancel' },
  ]],
});

export const donePhotosKeyboard = (): InlineKeyboardMarkup => ({
  inline_keyboard: [[
    { text: '✅ Готово',  callback_data: 'photos_done' },
    { text: '❌ Отмена',  callback_data: 'cancel' },
  ]],
});

export const confirmKeyboard = (
  confirmData: string,
  label = '✅ Подтвердить',
): InlineKeyboardMarkup => ({
  inline_keyboard: [[
    { text: label,        callback_data: confirmData },
    { text: '❌ Отмена',  callback_data: 'cancel' },
  ]],
});

// ── Admin ─────────────────────────────────────────────────────────────────────

export const usersForPromoteKeyboard = (
  users: Array<{ id: string; name: string; lastName?: string | null; role: string }>,
): InlineKeyboardMarkup => ({
  inline_keyboard: [
    ...users
      .filter(u => u.role === 'client')
      .map(u => ([{
        text:          `👤 ${userFullName(u)} → Менеджер`,
        callback_data: `promote:${u.id}`,
      }])),
    [{ text: '⬅️ Назад', callback_data: 'admin:menu' }],
  ],
});

export const usersForDemoteKeyboard = (
  users: Array<{ id: string; name: string; lastName?: string | null; role: string }>,
): InlineKeyboardMarkup => ({
  inline_keyboard: [
    ...users
      .filter(u => u.role === 'manager')
      .map(u => ([{
        text:          `🔧 ${userFullName(u)} → Клиент`,
        callback_data: `demote:${u.id}`,
      }])),
    [{ text: '⬅️ Назад', callback_data: 'admin:menu' }],
  ],
});
