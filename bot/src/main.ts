// apps/bot/src/main.ts
import 'dotenv/config';
import { Telegraf }  from 'telegraf';
import { message }   from 'telegraf/filters';
import { authViaTelegram, registerClient, api, getApiErrorMessage } from './api';
import { getSession, setSession, clearSession } from './session';
import {   adminReplyMenu, managerReplyMenu, clientReplyMenu,
            adminMenu, managerMenu, clientMenu, sessionExpiredMenu } from './keyboards';
import { getDefaultScaleUrl, getScaleUrl, setScaleUrl } from './scale-config';
import { userFullName } from './user-name';
import {
  ENGLISH_ADDRESS_HINT,
  ENGLISH_NAME_HINT,
  isEnglishAddress,
  isEnglishName,
} from './latin-validation';
import { logger } from './logger';

import {
  startReceive, handleTrackInput, handleCarrierSelect,
  handleSenderSelect, handleSenderInput,
  handleClientSelect, handleWeightSelect, handleWeightConfirm, handleWeightInput,
  handleItemSelect, handleItemNameInput,
  handleValueSelect, handleValueInput, finalizeReceive,
} from './handlers/receive.handler';

import { handleScan, showScannedEntityHistory } from './handlers/scan.handler';
import { handlePhoto }   from './handlers/photo.handler';
import {
  finishManagerOrderPhoto,
  showManagerOrder,
  showManagerOrders,
  startManagerOrderPhoto,
} from './handlers/manager-orders.handler';

import {
  listParcels, startNewParcel, handleParcelClientSelect,
  finalizeParcel, handleParcelWeight, addOrderToParcel, confirmFinalizeParcel,
  showParcelDetails,
} from './handlers/parcel.handler';

import {
  listBoxes, createNewBox, addParcelToBox,
  showBoxContents, finalizeAndShipBox, confirmShipment, showShipmentConfirmation,
  handleBoxWeightInput, handleBoxDimensionsInput, handleBoxAddressSelect,
} from './handlers/box.handler';

import {
  showAdminMenu, listUsers, showPromoteList,
  promoteToManager, showPromoteConfirmation,
  showDemoteList, showDemoteConfirmation, demoteToClient,
  showStats,
} from './handlers/admin.handler';
import {
  showManagerMenu, showClientMenu,
} from './handlers/menu.handler';

import {
  showClientOrders, showClientParcels, showClientParcel, showClientAddresses,
  showClientAnalytics, showClientFinance, showClientProfile,
  startClientEmailEdit, handleClientEmailInput,
  startClientNameEdit, handleClientNameInput, handleClientLastNameInput,
  startClientAddressEdit,
  handleClientAddressRecipient, handleClientAddressPhone,
  handleClientAddressCountry, handleClientAddressCity,
  handleClientAddressPostalCode, handleClientAddressStreet,
  handleClientAddressHouseNumber, handleClientAddressBuilding, handleClientAddressApartment,
  handleClientAddressRegion,
  startClientOrderRegistration, handleClientOrderTrack,
  handleClientOrderSender, handleClientOrderSenderSelect,
  handleClientOrderWeight, handleClientOrderDimensions,
} from './handlers/client.handler';

import {
  showDiscountClients, startDiscountEdit, handleDiscountInput, confirmDiscountUpdate,
} from './handlers/discount.handler';

import {
  showManagerClients, showManagerClientCard,
} from './handlers/manager-client.handler';

import { showStatusHistory } from './handlers/history.handler';
import { handleNoteText, showNotes, startNoteInput } from './handlers/note.handler';
import {
  confirmOrderItemDelete,
  confirmOrderItemHs,
  handleOrderItemEdit,
  handleOrderItemKeyword,
  handleOrderItemManual,
  handleOrderItemQtyPrice,
  showOrderItemDeleteConfirmation,
  showOrderItems,
  showOrderItemsByCommand,
  startOrderItemAdd,
  startOrderItemEdit,
  startOrderItemManual,
} from './handlers/order-items.handler';

// ── Инициализация ──────────────────────────────────────────────────────────────

const token = process.env.TELEGRAM_BOT_TOKEN?.trim();
if (!token) throw new Error('TELEGRAM_BOT_TOKEN is required');

const bot = new Telegraf(token);

bot.use(async (ctx, next) => {
  const fromId = ctx.from?.id ?? 'unknown';
  logger.debug(`[Bot Update] type=${ctx.updateType} from=${fromId}`);
  return next();
});

const TRACK_REGEX = /^[A-Z0-9._-]{8,30}$/i;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function isTokenExpired(token?: string): boolean {
  if (!token) return true;
  try {
    const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
    return typeof payload.exp !== 'number' || payload.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
}

async function replySessionExpired(ctx: any) {
  if (ctx.callbackQuery) {
    await ctx.answerCbQuery('Сессия завершена').catch(() => null);
  }
  await ctx.reply(
    '⏳ <b>Сессия завершена</b>\n\n' +
    'Это могло произойти из-за долгого перерыва или перезапуска бота.\n' +
    'Нажмите кнопку ниже, чтобы снова войти и продолжить работу.',
    { parse_mode: 'HTML', reply_markup: sessionExpiredMenu() },
  );
}

function isSkip(text: string): boolean {
  return ['skip', 'пропустить', 'нет', '-'].includes(text.trim().toLowerCase());
}

async function startRegistration(ctx: any, telegramId: string, name: string, username: string | null) {
  setSession(ctx.from.id, {
    step: 'registration:await_name',
    registration: { telegramId, name, username },
  });

  await ctx.reply(
    `Давайте создадим аккаунт.\n\nВведите имя английскими буквами.\n${ENGLISH_NAME_HINT}`,
  );
}

async function handleRegistrationName(ctx: any, text: string) {
  const session = getSession(ctx.from.id);
  if (!session.registration) return;
  if (!isEnglishName(text)) {
    await ctx.reply(`❌ Некорректное имя.\n${ENGLISH_NAME_HINT}`);
    return;
  }
  setSession(ctx.from.id, {
    step: 'registration:await_last_name',
    registration: { ...session.registration, name: text.trim() },
  });
  await ctx.reply(`Введите фамилию английскими буквами.\n${ENGLISH_NAME_HINT}`);
}

async function handleRegistrationLastName(ctx: any, text: string) {
  const session = getSession(ctx.from.id);
  if (!session.registration) return;
  if (!isEnglishName(text)) {
    await ctx.reply(`❌ Некорректная фамилия.\n${ENGLISH_NAME_HINT}`);
    return;
  }
  setSession(ctx.from.id, {
    step: 'registration:await_email',
    registration: { ...session.registration, lastName: text.trim() },
  });
  await ctx.reply('Введите email или напишите "пропустить".');
}

async function handleRegistrationEmail(ctx: any, text: string) {
  const session = getSession(ctx.from.id);
  if (!session.registration) return;

  if (!isSkip(text) && !EMAIL_REGEX.test(text)) {
    await ctx.reply('Похоже, email введён некорректно. Введите email ещё раз или напишите "пропустить".');
    return;
  }

  setSession(ctx.from.id, {
    step: 'registration:await_phone',
    registration: {
      ...session.registration,
      email: isSkip(text) ? undefined : text,
    },
  });

  await ctx.reply('Введите телефон или напишите "пропустить".');
}

async function handleRegistrationPhone(ctx: any, text: string) {
  const session = getSession(ctx.from.id);
  if (!session.registration) return;

  const phone = isSkip(text) ? null : text;
  setSession(ctx.from.id, {
    step: 'registration:await_address_country',
    registration: {
      ...session.registration,
      phone,
    },
  });

  await ctx.reply(
    `Введите страну доставки английскими буквами или напишите "пропустить", чтобы добавить адрес позже.\n` +
    ENGLISH_ADDRESS_HINT,
  );
}

async function handleRegistrationAddressCountry(ctx: any, text: string) {
  const session = getSession(ctx.from.id);
  const registration = session.registration;
  if (!registration) return;

  if (isSkip(text)) {
    await finishRegistration(ctx);
    return;
  }
  if (!isEnglishAddress(text)) {
    await ctx.reply(`❌ Страна указана некорректно.\n${ENGLISH_ADDRESS_HINT}`);
    return;
  }

  setSession(ctx.from.id, {
    step: 'registration:await_address_city',
    registration: {
      ...registration,
      address: {
        label:     'Основной',
        recipient: [registration.name, registration.lastName].filter(Boolean).join(' '),
        phone:     registration.phone || '',
        country:   text,
        isDefault: true,
      },
    },
  });

  await ctx.reply('Введите город доставки.');
}

async function handleRegistrationAddressCity(ctx: any, text: string) {
  const session = getSession(ctx.from.id);
  const registration = session.registration;
  if (!registration) return;
  if (!isEnglishAddress(text)) {
    await ctx.reply(`❌ Город указан некорректно.\n${ENGLISH_ADDRESS_HINT}`);
    return;
  }

  setSession(ctx.from.id, {
    step: 'registration:await_address_postal_code',
    registration: {
      ...registration,
      address: { ...registration.address, city: text },
    },
  });

  await ctx.reply('Введите почтовый индекс.');
}

async function handleRegistrationAddressPostalCode(ctx: any, text: string) {
  const session = getSession(ctx.from.id);
  const registration = session.registration;
  if (!registration) return;
  const postalCode = text.trim();
  if (!/^\d{2,10}$/.test(postalCode)) {
    await ctx.reply('❌ Индекс должен содержать только цифры.');
    return;
  }

  setSession(ctx.from.id, {
    step: 'registration:await_address_street',
    registration: {
      ...registration,
      address: { ...registration.address, postalCode },
    },
  });

  await ctx.reply(`Введите улицу английскими буквами.\n${ENGLISH_ADDRESS_HINT}`);
}

async function handleRegistrationAddressStreet(ctx: any, text: string) {
  const session = getSession(ctx.from.id);
  const registration = session.registration;
  if (!registration) return;
  if (!isEnglishAddress(text)) {
    await ctx.reply(`❌ Улица указана некорректно.\n${ENGLISH_ADDRESS_HINT}`);
    return;
  }

  setSession(ctx.from.id, {
    step: 'registration:await_address_region',
    registration: {
      ...registration,
      address: { ...registration.address, street: text },
    },
  });

  await ctx.reply('Введите регион/область или напишите "пропустить".');
}

async function handleRegistrationAddressRegion(ctx: any, text: string) {
  const session = getSession(ctx.from.id);
  const registration = session.registration;
  if (!registration) return;
  if (!isSkip(text) && !isEnglishAddress(text)) {
    await ctx.reply(`❌ Регион указан некорректно.\n${ENGLISH_ADDRESS_HINT}`);
    return;
  }

  setSession(ctx.from.id, {
    registration: {
      ...registration,
      address: {
        ...registration.address,
        region: isSkip(text) ? undefined : text,
      },
    },
  });

  await finishRegistration(ctx);
}

async function finishRegistration(ctx: any) {
  const session = getSession(ctx.from.id);
  const registration = session.registration;
  if (!registration) return;

  const addressDraft = registration.address;
  const address = addressDraft?.country && addressDraft.city && addressDraft.postalCode && addressDraft.street
    ? {
      label:      addressDraft.label ?? 'Основной',
      recipient:  addressDraft.recipient ?? [registration.name, registration.lastName].filter(Boolean).join(' '),
      phone:      addressDraft.phone ?? registration.phone ?? '',
      country:    addressDraft.country,
      city:       addressDraft.city,
      region:     addressDraft.region,
      postalCode: addressDraft.postalCode,
      street:     addressDraft.street,
      isDefault:  true,
    }
    : undefined;

  const reg = await registerClient({
    ...registration,
    address,
  });

  if (reg) {
    clearSession(ctx.from.id, false);
    setSession(ctx.from.id, { userToken: reg.token, userRole: 'client' });
    await ctx.reply(
      `👋 Добро пожаловать в <b>LMS</b>, <b>${[registration.name, registration.lastName].filter(Boolean).join(' ')}</b>!\n\n` +
      `Ваш аккаунт создан.`,
      { parse_mode: 'HTML', reply_markup: clientMenu() },
    );
  } else {
    await ctx.reply('❌ Ошибка регистрации. Обратитесь к менеджеру.');
  }
}

// ── /start ────────────────────────────────────────────────────────────────────

async function authorizeAndShowMenu(ctx: any) {
  clearSession(ctx.from.id, false);
  const telegramId = String(ctx.from.id);

  try {
    // Пробуем авторизоваться — если пользователь уже есть в системе
    const auth = await authViaTelegram(telegramId);

    if (auth) {
      // Сохраняем токен в сессии пользователя
      setSession(ctx.from.id, { userToken: auth.token, userRole: auth.user.role });

      if (auth.user.role === 'admin') {
        await ctx.reply(
          `👑 Привет, <b>${userFullName(auth.user, 'Пользователь')}</b>!\n\nПанель администратора:`,
          { parse_mode: 'HTML', reply_markup: adminMenu() },
        );
      } else if (auth.user.role === 'manager') {
        await ctx.reply(
          `🔧 Привет, <b>${userFullName(auth.user, 'Пользователь')}</b>!\n\nВыберите действие:`,
          { parse_mode: 'HTML', reply_markup: managerMenu() },
        );
      } else {
        await ctx.reply(
          `👋 Привет, <b>${userFullName(auth.user, 'Пользователь')}</b>!\n\nВаши заказы:`,
          { parse_mode: 'HTML', reply_markup: clientMenu() },
        );
      }
    } else {
      // Новый пользователь — регистрируем как клиента
      const name = [ctx.from.first_name, ctx.from.last_name]
        .filter(Boolean).join(' ');

      await ctx.reply(
        '👋 Я не нашел ваш Telegram в базе LMS.\n\n' +
        'Давайте быстро создадим клиентский аккаунт.',
      );
      await startRegistration(ctx, telegramId, name, ctx.from.username ?? null);
    }
  } catch {
    await ctx.reply('❌ Ошибка подключения к серверу.');
  }
}

bot.use(async (ctx, next) => {
  const text = (ctx.message as any)?.text?.trim() ?? '';
  const callbackData = (ctx.callbackQuery as any)?.data ?? '';
  const session = ctx.from ? getSession(ctx.from.id) : null;
  const isStart = /^\/start(?:@\w+)?(?:\s|$)/i.test(text);
  const isRestart = callbackData === 'session:restart';
  const isRegistration = Boolean(session?.registration);

  if (isStart || isRestart || isRegistration) {
    return next();
  }
  if (!session || isTokenExpired(session.userToken)) {
    if (ctx.from) clearSession(ctx.from.id, false);
    await replySessionExpired(ctx);
    return;
  }
  return next();
});

bot.command('start', authorizeAndShowMenu);

// ── Команды ───────────────────────────────────────────────────────────────────

bot.command('menu', async ctx => {
  const session = getSession(ctx.from.id);

  if (session.userRole === 'admin') {
    await showAdminMenu(ctx);
  } else if (session.userRole === 'client') {
    await showClientMenu(ctx);
  } else {
    await showManagerMenu(ctx);
  }
});
bot.command('hide_menu', async ctx => {
  await ctx.reply('Нижнее меню скрыто. Основное меню доступно по /menu.', {
    reply_markup: { remove_keyboard: true },
  });
});
bot.command('buttons', async ctx => {
  const session = getSession(ctx.from.id);

  if (session.userRole === 'admin') {
    await ctx.reply('Нижние кнопки включены. Скрыть: /hide_menu', { reply_markup: adminReplyMenu() });
  } else if (session.userRole === 'client') {
    await ctx.reply('Нижние кнопки включены. Скрыть: /hide_menu', { reply_markup: clientReplyMenu() });
  } else {
    await ctx.reply('Нижние кнопки включены. Скрыть: /hide_menu', { reply_markup: managerReplyMenu() });
  }
});
bot.command('admin',      ctx => showAdminMenu(ctx));
bot.command('receive',    ctx => startReceive(ctx));
bot.command('parcel_new', ctx => startNewParcel(ctx));
bot.command('parcel_list',ctx => listParcels(ctx));
bot.command('box_new',    ctx => createNewBox(ctx));
bot.command('box_list',   ctx => listBoxes(ctx));
bot.command('clients',    ctx => showManagerClients(ctx));
bot.command('history',    ctx => showStatusHistory(ctx));
bot.command('finance',    ctx => showClientFinance(ctx));
bot.command('items', async ctx => {
  const text = ((ctx.message as any).text ?? '') as string;
  const query = text.replace(/^\/items(@\w+)?\s*/i, '').trim();
  await showOrderItemsByCommand(ctx, query);
});
bot.command('notes', async ctx => {
  const text = ((ctx.message as any).text ?? '') as string;
  const [, entityType, entityId] = text.trim().split(/\s+/);

  if (!entityType || !entityId) {
    await ctx.reply(
      '💬 Комментарии:\n' +
      '<code>/notes order ORDER_ID</code>\n' +
      '<code>/notes parcel PARCEL_ID</code>\n' +
      '<code>/notes box BOX_ID</code>',
      { parse_mode: 'HTML' },
    );
    return;
  }

  await showNotes(ctx, entityType, entityId);
});
bot.command('scale', async ctx => {
  const role = getSession(ctx.from.id).userRole;
  if (role === 'client') {
    await ctx.reply('❌ Эта команда доступна только менеджеру.');
    return;
  }

  await ctx.reply(
    `⚖️ Адрес весов: <code>${getScaleUrl()}</code>\n` +
    `Адрес из .env: <code>${getDefaultScaleUrl()}</code>\n\n` +
    `Изменить: <code>/scale_url 192.168.1.50</code>`,
    { parse_mode: 'HTML' },
  );
});
bot.command('scale_url', async ctx => {
  const role = getSession(ctx.from.id).userRole;
  if (role === 'client') {
    await ctx.reply('❌ Эта команда доступна только менеджеру.');
    return;
  }

  const text = ((ctx.message as any).text ?? '') as string;
  const nextUrl = text.replace(/^\/scale_url(@\w+)?\s*/i, '').trim();

  if (!nextUrl) {
    await ctx.reply(
      `⚖️ Текущий адрес весов: <code>${getScaleUrl()}</code>\n\n` +
      `Укажите новый адрес:\n<code>/scale_url 192.168.1.50</code>\n` +
      `или <code>/scale_url http://192.168.1.50:8080/weight</code>`,
      { parse_mode: 'HTML' },
    );
    return;
  }

  const updated = setScaleUrl(nextUrl);
  if (!updated) {
    await ctx.reply('❌ Некорректный адрес. Пример: /scale_url 192.168.1.50');
    return;
  }

  await ctx.reply(
    `✅ Адрес весов обновлен:\n<code>${updated}</code>\n\n` +
    `Важно: это изменение действует до перезапуска бота. Для постоянной настройки укажите SCALE_URL в .env.`,
    { parse_mode: 'HTML' },
  );
});
bot.command('cancel', async ctx => {
  const role = getSession(ctx.from.id).userRole;
  clearSession(ctx.from.id);
  if (role === 'admin') {
    await ctx.reply('❌ Отменено.', { reply_markup: adminMenu() });
  } else if (role === 'client') {
    await ctx.reply('❌ Отменено.', { reply_markup: clientMenu() });
  } else {
    await ctx.reply('❌ Отменено.', { reply_markup: managerMenu() });
  }
});

// ── Callback кнопки ───────────────────────────────────────────────────────────

bot.on('callback_query', async ctx => {
  const data       = (ctx.callbackQuery as any).data as string;
  const telegramId = ctx.from.id;
  const session    = getSession(telegramId);

  await ctx.answerCbQuery();

  if (data === 'session:restart') {
    await authorizeAndShowMenu(ctx);
    return;
  }

  // Отмена
  if (data === 'cancel') {
    const role = session.userRole;
    clearSession(telegramId);
    await ctx.editMessageReplyMarkup(undefined).catch(() => null);
    if (role === 'admin') {
      await ctx.reply('❌ Отменено.', { reply_markup: adminMenu() });
    } else if (role === 'client') {
      await ctx.reply('❌ Отменено.', { reply_markup: clientMenu() });
    } else {
      await ctx.reply('❌ Отменено.', { reply_markup: managerMenu() });
    }
    return;
  }

  // Главные меню
  if (data === 'admin:menu')    { await showAdminMenu(ctx);    return; }
  if (data === 'admin:users')   { await listUsers(ctx);        return; }
  if (data === 'admin:promote') { await showPromoteList(ctx);  return; }
  if (data === 'admin:demote')  { await showDemoteList(ctx);   return; }
  if (data === 'admin:stats')   { await showStats(ctx);        return; }
  if (data === 'switch:manager'){ await showManagerMenu(ctx);   return; }
  if (data === 'manager:clients'){ await showManagerClients(ctx); return; }
  if (data === 'manager:orders') { await showManagerOrders(ctx); return; }
  if (data.startsWith('manager_order:view:')) {
    await showManagerOrder(ctx, data.slice('manager_order:view:'.length));
    return;
  }
  if (data.startsWith('manager_order:photo:')) {
    await startManagerOrderPhoto(ctx, data.slice('manager_order:photo:'.length));
    return;
  }
  if (data === 'manager_order:photos_done') {
    await finishManagerOrderPhoto(ctx);
    return;
  }

  if (data === 'client_menu:orders')    { await showClientOrders(ctx);    return; }
  if (data === 'client_order:new')      { await startClientOrderRegistration(ctx); return; }
  if (data === 'client_menu:parcels')   { await showClientParcels(ctx);   return; }
  if (data.startsWith('client_parcel:')) {
    await showClientParcel(ctx, data.split(':')[1]);
    return;
  }
  if (data === 'client_menu:addresses') { await showClientAddresses(ctx); return; }
  if (data === 'client_profile:name')   { await startClientNameEdit(ctx); return; }
  if (data === 'client_address:new')    { await startClientAddressEdit(ctx); return; }
  if (data.startsWith('client_address:edit:')) {
    await startClientAddressEdit(ctx, data.split(':')[2]);
    return;
  }
  if (data === 'client_menu:analytics') { await showClientAnalytics(ctx); return; }
  if (data === 'client_menu:finance')   { await showClientFinance(ctx);   return; }
  if (data === 'client_menu:profile')   { await showClientProfile(ctx);   return; }
  if (data === 'client_profile:email')  { await startClientEmailEdit(ctx); return; }

  if (data === 'receive')      { await startReceive(ctx);   return; }
  if (data === 'parcel_new')   { await startNewParcel(ctx); return; }
  if (data === 'parcel_list')  { await listParcels(ctx);    return; }
  if (data === 'box_new')      { await createNewBox(ctx);   return; }
  if (data === 'box_list')     { await listBoxes(ctx);      return; }
  if (data === 'discount:list'){ await showDiscountClients(ctx); return; }

  if (data.startsWith('discount_client:')) {
    await startDiscountEdit(ctx, data.split(':')[1]);
    return;
  }

  if (data.startsWith('manager_client:')) {
    await showManagerClientCard(ctx, data.split(':')[1]);
    return;
  }

  if (data.startsWith('scan_history:')) {
    const [, entityType, entityId] = data.split(':');
    await showScannedEntityHistory(ctx, entityType, entityId);
    return;
  }

  if (data.startsWith('notes:')) {
    const [, entityType, entityId] = data.split(':');
    await showNotes(ctx, entityType, entityId);
    return;
  }

  if (data.startsWith('note_add:')) {
    const [, entityType, entityId] = data.split(':');
    await startNoteInput(ctx, entityType, entityId);
    return;
  }

  if (data.startsWith('oi_del_ok:')) {
    const [, orderId, itemId] = data.split(':');
    await confirmOrderItemDelete(ctx, orderId, itemId);
    return;
  }

  if (data.startsWith('oi_del:')) {
    const [, orderId, itemId] = data.split(':');
    await showOrderItemDeleteConfirmation(ctx, orderId, itemId);
    return;
  }

  if (data.startsWith('oi_edit:')) {
    const [, orderId, itemId] = data.split(':');
    await startOrderItemEdit(ctx, orderId, itemId);
    return;
  }

  if (data.startsWith('oi_add:')) {
    await startOrderItemAdd(ctx, data.split(':')[1]);
    return;
  }

  if (data === 'oi_ok') {
    await confirmOrderItemHs(ctx);
    return;
  }

  if (data === 'oi_manual') {
    await startOrderItemManual(ctx);
    return;
  }

  if (data.startsWith('oi:')) {
    await showOrderItems(ctx, data.split(':')[1]);
    return;
  }

  if (data.startsWith('discount_confirm:')) {
    const [, clientId, discount] = data.split(':');
    await confirmDiscountUpdate(ctx, clientId, parseFloat(discount));
    return;
  }

  // Admin — назначить менеджера
  if (data.startsWith('promote_confirm:')) {
    await promoteToManager(ctx, data.split(':')[1]);
    return;
  }
  if (data.startsWith('promote:')) {
    await showPromoteConfirmation(ctx, data.split(':')[1]);
    return;
  }
  if (data.startsWith('demote_confirm:')) {
    await demoteToClient(ctx, data.split(':')[1]);
    return;
  }
  if (data.startsWith('demote:')) {
    await showDemoteConfirmation(ctx, data.split(':')[1]);
    return;
  }

  // Входящий перевозчик
  if (data.startsWith('inbound_carrier:')) {
    await handleCarrierSelect(ctx, data.split(':')[1]);
    return;
  }

  // Отправитель
  if (data.startsWith('client_sender:')) {
    await handleClientOrderSenderSelect(ctx, data.slice('client_sender:'.length));
    return;
  }
  if (data.startsWith('sender:')) {
    await handleSenderSelect(ctx, data.slice('sender:'.length));
    return;
  }

  if (data === 'client_order:skip_weight' && session.step === 'client_order:await_weight') {
    await handleClientOrderWeight(ctx, 'skip');
    return;
  }

  if (data === 'client_order:skip_dimensions' && session.step === 'client_order:await_dimensions') {
    await handleClientOrderDimensions(ctx, 'skip');
    return;
  }

  // Клиент
  if (data.startsWith('client:')) {
    const clientId = data.split(':')[1];
    if (session.step === 'receive:await_client') {
      await handleClientSelect(ctx, clientId);
    } else if (session.step === 'parcel:await_client') {
      await handleParcelClientSelect(ctx, clientId);
    }
    return;
  }

  // Подтверждение веса с весов
  if (data.startsWith('weight_confirm:')) {
    const weight = parseFloat(data.split(':')[1]);
    await handleWeightConfirm(ctx, weight);
    return;
  }

  // Вес
  if (data.startsWith('weight:')) {
    await handleWeightSelect(ctx, data.split(':')[1]);
    return;
  }

  // Категория товара
  if (data.startsWith('item:')) {
    await handleItemSelect(ctx, data.split(':')[1]);
    return;
  }

  // Стоимость
  if (data.startsWith('value:')) {
    await handleValueSelect(ctx, data.split(':')[1]);
    return;
  }

  // Пропустить
  if (data === 'skip') {
    if (session.step === 'receive:await_item_name') {
      await handleItemNameInput(ctx, null);
    } else if (session.step === 'receive:await_value') {
      await handleValueInput(ctx, null);
    }
    return;
  }

  // Фото готово
  if (data === 'photos_done') {
    await finalizeReceive(ctx);
    return;
  }

  // Parcel
  if (data.startsWith('parcel_finalize:')) {
    await finalizeParcel(ctx, data.split(':')[1]);
    return;
  }
  if (data.startsWith('parcel_confirm_finalize:')) {
    const [, parcelId, weight] = data.split(':');
    await confirmFinalizeParcel(ctx, parcelId, parseFloat(weight));
    return;
  }
  if (data.startsWith('parcel:')) {
    const parcelId = data.split(':')[1];
    if (session.parcel?.orderIds?.length) {
      await addOrderToParcel(ctx, parcelId, session.parcel.orderIds[0]);
      clearSession(telegramId);
      return;
    }
    await showParcelDetails(ctx, parcelId);
    return;
  }

  // Box
  if (data.startsWith('box:')) {
    await showBoxContents(ctx, data.split(':')[1]);
    return;
  }
  if (data.startsWith('box_finalize:')) {
    await finalizeAndShipBox(ctx, data.split(':')[1]);
    return;
  }
  if (data.startsWith('box_address:')) {
    await handleBoxAddressSelect(ctx, data.split(':')[1]);
    return;
  }

  // Тариф: rate:{serviceCode}:{carrierId}:{price}
  if (data.startsWith('rate:')) {
    const parts = data.split(':');
    const serviceCode = parts[1];
    const carrierId   = parts[2];
    const price       = parseFloat(parts[3]);
    const msgText     = (ctx.callbackQuery as any).message?.text ?? '';
    const match       = msgText.match(/#([A-Za-z0-9]+)/);
    if (!match) { await ctx.reply('❌ Не найден бокс.'); return; }

    const boxes = await api.get('/boxes').then(r => r.data as any[]);
    const box   = boxes.find((b: any) => b.id.endsWith(match[1]));
    if (!box)   { await ctx.reply('❌ Бокс не найден.'); return; }

    await showShipmentConfirmation(ctx, box.id, serviceCode, carrierId, price);
    return;
  }

  if (data.startsWith('ship_confirm:')) {
    const [, boxId, serviceCode, carrierId, price] = data.split(':');
    await confirmShipment(ctx, boxId, serviceCode, carrierId, parseFloat(price));
    return;
  }
});

// ── Текст ─────────────────────────────────────────────────────────────────────

bot.on(message('text'), async ctx => {
  const telegramId = ctx.from.id;
  const session    = getSession(telegramId);
  const text       = ctx.message.text.trim();

  //if (text.startsWith('/')) return;

  if (session.noteDraft && !text.startsWith('/')) {
    await handleNoteText(ctx, text);
    return;
  }

  if (!text.startsWith('/')) {
  const replyActions: Record<string, () => void> = {
    '📦 Принять посылку': () => startReceive(ctx),
    '📦 Приемка':         () => startReceive(ctx),
    '📋 Заказы':          () => showManagerOrders(ctx),
    '👤 Клиенты':         () => showManagerClients(ctx),
    '📋 Отправления':     () => listParcels(ctx),
    '📬 Отправления':     () => session.userRole === 'client' ? showClientParcels(ctx) : listParcels(ctx),
    '🗃 Боксы':           () => listBoxes(ctx),
    '💸 Скидки':          () => showDiscountClients(ctx),
    '👥 Пользователи':    () => listUsers(ctx),
    '📊 Статистика':      () => showStats(ctx),
    '🔧 Меню менеджера':  () => ctx.reply('Меню:', { reply_markup: managerMenu() }),
    '👑 Админ меню':      () => showAdminMenu(ctx),
    '➕ Назначить менеджера': () => showPromoteList(ctx),
    '➖ Сделать клиентом':     () => showDemoteList(ctx),
    '📦 Мои заказы':      () => showClientOrders(ctx),
    '➕ Новый заказ':      () => startClientOrderRegistration(ctx),
    '📍 Адреса':          () => showClientAddresses(ctx),
    '📊 Аналитика':       () => showClientAnalytics(ctx),
    '💸 Финансы':         () => showClientFinance(ctx),
    '👤 Профиль':         () => showClientProfile(ctx),
  };

  const action = replyActions[text];
  if (action) { await action(); return; }
}
  switch (session.step) {
    case 'registration:await_email':
      await handleRegistrationEmail(ctx, text); break;
    case 'registration:await_name':
      await handleRegistrationName(ctx, text); break;
    case 'registration:await_last_name':
      await handleRegistrationLastName(ctx, text); break;
    case 'registration:await_phone':
      await handleRegistrationPhone(ctx, text); break;
    case 'registration:await_address_country':
      await handleRegistrationAddressCountry(ctx, text); break;
    case 'registration:await_address_city':
      await handleRegistrationAddressCity(ctx, text); break;
    case 'registration:await_address_postal_code':
      await handleRegistrationAddressPostalCode(ctx, text); break;
    case 'registration:await_address_street':
      await handleRegistrationAddressStreet(ctx, text); break;
    case 'registration:await_address_region':
      await handleRegistrationAddressRegion(ctx, text); break;
    case 'client:await_email':
      await handleClientEmailInput(ctx, text); break;
    case 'client:await_name':
      await handleClientNameInput(ctx, text); break;
    case 'client:await_last_name':
      await handleClientLastNameInput(ctx, text); break;
    case 'client_address:await_recipient':
      await handleClientAddressRecipient(ctx, text); break;
    case 'client_address:await_phone':
      await handleClientAddressPhone(ctx, text); break;
    case 'client_address:await_country':
      await handleClientAddressCountry(ctx, text); break;
    case 'client_address:await_city':
      await handleClientAddressCity(ctx, text); break;
    case 'client_address:await_postal_code':
      await handleClientAddressPostalCode(ctx, text); break;
    case 'client_address:await_street':
      await handleClientAddressStreet(ctx, text); break;
    case 'client_address:await_house_number':
      await handleClientAddressHouseNumber(ctx, text); break;
    case 'client_address:await_building':
      await handleClientAddressBuilding(ctx, text); break;
    case 'client_address:await_apartment':
      await handleClientAddressApartment(ctx, text); break;
    case 'client_address:await_region':
      await handleClientAddressRegion(ctx, text); break;
    case 'client_order:await_track':
      await handleClientOrderTrack(ctx, text); break;
    case 'client_order:await_sender':
      await handleClientOrderSender(ctx, text); break;
    case 'client_order:await_weight':
      await handleClientOrderWeight(ctx, text); break;
    case 'client_order:await_dimensions':
      await handleClientOrderDimensions(ctx, text); break;
    case 'receive:await_track':
      await handleTrackInput(ctx, text); break;
    case 'receive:await_sender':
      await handleSenderInput(ctx, text); break;
    case 'receive:await_weight':
      await handleWeightInput(ctx, text); break;
    case 'receive:await_item_name':
      await handleItemNameInput(ctx, text); break;
    case 'receive:await_value':
      await handleValueInput(ctx, text); break;
    case 'parcel:await_weight':
      await handleParcelWeight(ctx, text); break;
    case 'box:await_weight':
      await handleBoxWeightInput(ctx, text); break;
    case 'box:await_dimensions':
      await handleBoxDimensionsInput(ctx, text); break;
    case 'note:await_text':
      await handleNoteText(ctx, text); break;
    case 'order_item:await_keyword':
      await handleOrderItemKeyword(ctx, text); break;
    case 'order_item:await_manual_hs':
      await handleOrderItemManual(ctx, text); break;
    case 'order_item:await_qty_price':
      await handleOrderItemQtyPrice(ctx, text); break;
    case 'order_item:await_edit':
      await handleOrderItemEdit(ctx, text); break;
    case 'manager:await_discount':
      await handleDiscountInput(ctx, text); break;
    default:
      if (TRACK_REGEX.test(text)) {
        await handleScan(ctx, text);
      } else {
        await ctx.reply('Используйте кнопки или отсканируйте трек.\n/menu — главное меню');
      }
  }
});

// ── Фото ─────────────────────────────────────────────────────────────────────

bot.on(message('photo'), async ctx => {
  await handlePhoto(ctx);
});

// ── Запуск ────────────────────────────────────────────────────────────────────

logger.info('Starting LMS Bot...');
logger.info(`Backend: ${process.env.BACKEND_URL ?? 'http://localhost:3000/api/v1'}`);

bot.catch(async (err, ctx) => {
  logger.error('[Bot Error]', err);
  try {
    await ctx.reply(
      `❌ ${getApiErrorMessage(err)}\n` +
      'Попробуйте еще раз или нажмите /cancel и начните заново.',
    );
  } catch (replyErr) {
    logger.error('[Bot Error Reply Failed]', replyErr);
  }
});

process.on('unhandledRejection', err => {
  logger.error('Unhandled rejection:', err);
});

async function launchBot() {
  try {
    const me = await bot.telegram.getMe();
    logger.info(`Telegram bot: @${me.username ?? me.first_name}`);
    await bot.telegram.deleteWebhook({ drop_pending_updates: true });
    logger.info('Telegram webhook cleared');
    void bot.launch().catch(err => {
      logger.error('Telegram polling failed:', err);
      process.exitCode = 1;
    });
    logger.info('Telegram polling started');
    logger.info(`Backend: ${process.env.BACKEND_URL}`);
  } catch (err) {
    logger.error('Failed to start LMS Bot:', err);
    process.exitCode = 1;
  }
}

void launchBot();

process.once('SIGINT',  () => bot.stop('SIGINT'));
process.once('SIGTERM', () => bot.stop('SIGTERM'));
