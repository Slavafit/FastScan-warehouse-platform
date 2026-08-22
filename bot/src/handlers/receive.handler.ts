// apps/bot/src/handlers/receive.handler.ts

import { Context } from 'telegraf';
import * as api    from '../api';
import { getSession, setSession, clearSession } from '../session';
import {
  carriersKeyboard, clientsKeyboard,
  weightKeyboard, senderKeyboard, itemCategoryKeyboard,
  donePhotosKeyboard, managerMenu,
} from '../keyboards';
import { getScaleUrl } from '../scale-config';

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function normalizeTrack(track: string): string {
  return track.trim().toUpperCase();
}

function apiErrorMessage(err: any): string {
  return api.getApiErrorMessage(err);
}

// ── Старт приёмки ─────────────────────────────────────────────────────────────

export async function startReceive(ctx: Context) {
  const telegramId = ctx.from!.id;
  setSession(telegramId, { step: 'receive:await_track', receive: {} });

  await ctx.reply(
    '📦 <b>Приёмка посылки</b>\n\nВведите или отсканируйте входящий трек-номер:',
    { parse_mode: 'HTML' },
  );
}

// ── Обработка трека ───────────────────────────────────────────────────────────

export async function handleTrackInput(ctx: Context, track: string) {
  const telegramId = ctx.from!.id;
  const normalizedTrack = normalizeTrack(track);

  if (!normalizedTrack) {
    await ctx.reply('❌ Введите или отсканируйте трек-номер.');
    return;
  }
  if (normalizedTrack.length < 8 || normalizedTrack.length > 30) {
    await ctx.reply('❌ Трек должен содержать от 8 до 30 символов.');
    return;
  }
  if (!/^[A-Z0-9._-]+$/.test(normalizedTrack)) {
    await ctx.reply('❌ Используйте только латинские буквы, цифры и символы «-», «_», «.».');
    return;
  }

  try {
    const existing = await api.findOrderByTrack(normalizedTrack);
    if (existing) {
      if (existing.status === 'new' || existing.status === 'expected') {
        setSession(telegramId, {
          step: 'receive:await_carrier',
          receive: {
            inTrack: normalizedTrack,
            orderId: existing.id,
            clientId: existing.clientId,
            senderName: existing.senderName ?? undefined,
            weight: existing.weight,
          },
        });
        const carriers = await api.getInboundCarriers();
        await ctx.reply(
          `✅ Найден предварительно зарегистрированный заказ.\n` +
          `Трек: <code>${escapeHtml(normalizedTrack)}</code>\n\n` +
          `Выберите входящего перевозчика:`,
          { parse_mode: 'HTML', reply_markup: carriersKeyboard(carriers, 'inbound_carrier') },
        );
        return;
      }
      await ctx.reply(
        `⚠️ Трек <code>${escapeHtml(normalizedTrack)}</code> уже принят.\n` +
        `Статус: <b>${escapeHtml(existing.status)}</b>\n` +
        `Заказ: <code>${escapeHtml(existing.id?.slice(-8) ?? existing.id)}</code>\n\n` +
        `Повторная приемка отменена.`,
        { parse_mode: 'HTML' },
      );
      clearSession(telegramId);
      return;
    }
  } catch (err: any) {
    await ctx.reply(
      `❌ Не удалось проверить уникальность трека.\n\n` +
      `Причина: ${escapeHtml(apiErrorMessage(err))}\n\n` +
      `Приемка остановлена, чтобы не создать дубль.`,
      { parse_mode: 'HTML' },
    );
    clearSession(telegramId);
    return;
  }

  setSession(telegramId, {
    step:    'receive:await_carrier',
    receive: { inTrack: normalizedTrack },
  });

  try {
    const carriers = await api.getInboundCarriers();
    await ctx.reply(
      `✅ Трек: <code>${escapeHtml(normalizedTrack)}</code>\n\nВыберите входящего перевозчика:`,
      { parse_mode: 'HTML', reply_markup: carriersKeyboard(carriers, 'inbound_carrier') },
    );
  } catch {
    await ctx.reply('❌ Ошибка загрузки перевозчиков.');
    clearSession(telegramId);
  }
}

// ── Выбор перевозчика ─────────────────────────────────────────────────────────

export async function handleCarrierSelect(ctx: Context, carrierId: string) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);

  setSession(telegramId, {
    step:    'receive:await_sender',
    receive: { ...session.receive, incomingCarrierId: carrierId },
  });

  let senders = await api.getSenders().catch(() => []);
  if (!senders.length) {
    await api.seedSenders().catch(() => null);
    senders = await api.getSenders().catch(() => []);
  }

  await ctx.editMessageText(
    '✅ Перевозчик выбран.\n\nВыберите отправителя:',
    { parse_mode: 'HTML', reply_markup: senderKeyboard(senders) },
  );
}

async function askClient(ctx: Context, telegramId: number, senderName: string) {
  const session = getSession(telegramId);

  if (session.receive?.clientId) {
    setSession(telegramId, {
      step: 'receive:await_weight',
      receive: { ...session.receive, senderName },
    });
    await ctx.reply(
      `✅ Отправитель: <b>${escapeHtml(senderName)}</b>\n` +
      `Клиент определён по предварительной регистрации.\n\n` +
      `Укажите фактический вес:`,
      { parse_mode: 'HTML', reply_markup: weightKeyboard() },
    );
    return;
  }

  setSession(telegramId, {
    step:    'receive:await_client',
    receive: { ...session.receive, senderName },
  });

  try {
    const clients = await api.getClients();
    const text = `✅ Отправитель: <b>${senderName}</b>\n\nВыберите клиента:`;

    await ctx.reply(
      text,
      { parse_mode: 'HTML', reply_markup: clientsKeyboard(clients) },
    );
  } catch {
    await ctx.reply('❌ Ошибка загрузки клиентов.');
    clearSession(telegramId);
  }
}

export async function handleSenderSelect(ctx: Context, value: string) {
  const telegramId = ctx.from!.id;

  if (value === 'manual') {
    setSession(telegramId, { step: 'receive:await_sender' });
    await ctx.editMessageText(
      '✏️ Введите отправителя, например: <code>Zara</code>',
      { parse_mode: 'HTML' },
    );
    return;
  }

  await ctx.editMessageReplyMarkup(undefined).catch(() => null);

  const sender = await api.getSender(value).catch(() => null);
  await askClient(ctx, telegramId, sender?.name ?? value);
}

export async function handleSenderInput(ctx: Context, text: string) {
  const telegramId = ctx.from!.id;
  const senderName = text.trim();

  if (!senderName) {
    await ctx.reply('❌ Введите название отправителя.');
    return;
  }

  const sender = await api.createSender(senderName).catch(() => null);
  await askClient(ctx, telegramId, sender?.name ?? senderName);
}

// ── Выбор клиента ─────────────────────────────────────────────────────────────

export async function handleClientSelect(ctx: Context, clientId: string) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);

  setSession(telegramId, {
    step:    'receive:await_weight',
    receive: { ...session.receive, clientId },
  });

  await ctx.editMessageText(
    '✅ Клиент выбран.\n\nВыберите вес посылки:',
    { parse_mode: 'HTML', reply_markup: weightKeyboard() },
  );
}

// ── Ввод веса (кнопка весов, кнопка вручную, или текст) ──────────────────────

export async function handleWeightSelect(ctx: Context, value: string) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);

  if (value === 'scale') {
    // Запрашиваем вес с весов
    const scaleUrl = getScaleUrl();
    try {
      const res      = await fetch(scaleUrl, { signal: AbortSignal.timeout(3000) });
      const data     = await res.json() as { weight: number; unit: string };
      const weight   = parseFloat(data.weight.toFixed(3));

      if (!weight || weight <= 0) {
        await ctx.editMessageText(
          '⚠️ Весы показывают 0. Положите посылку и попробуйте снова:',
          { reply_markup: weightKeyboard() },
        );
        return;
      }

      // Показываем вес и просим подтвердить
      setSession(telegramId, {
        receive: { ...session.receive, weight },
      });

      await ctx.editMessageText(
        `⚖️ Вес с весов: <b>${weight} кг</b>\n\nПодтвердить?`,
        {
          parse_mode:   'HTML',
          reply_markup: {
            inline_keyboard: [
              [{ text: `✅ Да, ${weight} кг`, callback_data: `weight_confirm:${weight}` }],
              [{ text: '🔄 Перечитать весы',   callback_data: 'weight:scale' }],
              [{ text: '✏️ Ввести вручную',    callback_data: 'weight:manual' }],
              [{ text: '❌ Отмена',             callback_data: 'cancel' }],
            ],
          },
        },
      );
    } catch {
      await ctx.editMessageText(
        `❌ Весы недоступны по адресу ${scaleUrl}.\n\n` +
        `Проверьте подключение, измените адрес командой /scale_url или введите вес вручную:`,
        { reply_markup: weightKeyboard() },
      );
    }
    return;
  }

  if (value === 'manual') {
    setSession(telegramId, { step: 'receive:await_weight' });
    await ctx.editMessageText(
      '✏️ Введите вес в кг (например: <code>1.8</code>):',
      { parse_mode: 'HTML' },
    );
    return;
  }
}

export async function handleWeightConfirm(ctx: Context, weight: number) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);

  setSession(telegramId, {
    step:    'receive:await_item_name',
    receive: { ...session.receive, weight },
  });

  await ctx.editMessageText(
    `✅ Вес: ${weight} кг\n\nВыберите категорию товара:`,
    { parse_mode: 'HTML', reply_markup: itemCategoryKeyboard() },
  );
}

export async function handleWeightInput(ctx: Context, text: string) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);
  const weight     = parseFloat(text.replace(',', '.'));

  if (isNaN(weight) || weight <= 0) {
    await ctx.reply('❌ Введите число, например: <code>1.5</code>', { parse_mode: 'HTML' });
    return;
  }

  setSession(telegramId, {
    step:    'receive:await_item_name',
    receive: { ...session.receive, weight },
  });

  await ctx.reply(
    `✅ Вес: ${weight} кг\n\nВыберите категорию товара:`,
    { parse_mode: 'HTML', reply_markup: itemCategoryKeyboard() },
  );
}

// ── Выбор категории товара (кнопка или текст) ─────────────────────────────────

export async function handleItemSelect(ctx: Context, value: string) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);

  if (value === 'manual') {
    setSession(telegramId, { step: 'receive:await_item_name' });
    await ctx.editMessageText(
      '✏️ Введите название товара на русском:',
      { parse_mode: 'HTML' },
    );
    return;
  }

  // Стоимость необязательна — сразу создаём заказ
  await createOrderAndAskPhoto(ctx, telegramId, {
    ...session,
    receive: { ...session.receive, itemNameRu: value },
  });
}

export async function handleItemNameInput(ctx: Context, itemNameRu: string | null) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);

  // Стоимость необязательна — сразу создаём заказ
  await createOrderAndAskPhoto(ctx, telegramId, {
    ...session,
    receive: { ...session.receive, itemNameRu: itemNameRu ?? undefined },
  });
}

// Стоимость (declaredValue) добавляется позже при составлении таможенной декларации
// Эти методы оставляем для обратной совместимости, но не вызываем в основном флоу
export async function handleValueSelect(ctx: Context, value: string) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);
  if (value === 'manual') {
    setSession(telegramId, { step: 'receive:await_value' });
    await ctx.editMessageText('✏️ Введите стоимость в EUR:', { parse_mode: 'HTML' });
    return;
  }
  await createOrderAndAskPhoto(ctx, telegramId, session, parseFloat(value));
}

export async function handleValueInput(ctx: Context, text: string | null) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);
  const val        = text ? parseFloat(text.replace(',', '.')) : undefined;
  await createOrderAndAskPhoto(ctx, telegramId, session, isNaN(val!) ? undefined : val);
}

// ── Создать заказ и попросить фото ────────────────────────────────────────────

async function createOrderAndAskPhoto(
  ctx:          Context,
  telegramId:   number,
  session:      ReturnType<typeof getSession>,
  declaredValue?: number,
) {
  try {
    const r     = session.receive!;
    const order = await api.createOrder({
      clientId:          r.clientId!,
      inTrack:           r.inTrack!,
      incomingCarrierId: r.incomingCarrierId,
      senderName:        r.senderName,
      weight:            r.weight!,
      itemNameRu:        r.itemNameRu,
      declaredValue,
    });

    setSession(telegramId, {
      step:    'receive:await_photo',
      receive: { ...session.receive, declaredValue, orderId: order.id },
    });

    await ctx.reply(
      `✅ <b>Заказ создан!</b>\n` +
      `📦 Трек: <code>${order.inTrack}</code>\n\n` +
      `📷 Отправьте фото содержимого.\n` +
      `Можно несколько. Когда готово — нажмите «Готово».`,
      { parse_mode: 'HTML', reply_markup: donePhotosKeyboard() },
    );
  } catch (err: any) {
    const status = err?.response?.status;
    if (status === 409) {
      await ctx.reply(
        `⚠️ Заказ с треком <code>${escapeHtml(session.receive?.inTrack)}</code> уже существует.\n` +
        `Повторная приемка отменена.`,
        { parse_mode: 'HTML' },
      );
    } else {
      await ctx.reply(`❌ Ошибка создания заказа: ${escapeHtml(apiErrorMessage(err))}`, { parse_mode: 'HTML' });
    }
    clearSession(telegramId);
  }
}

// ── Завершить приёмку ─────────────────────────────────────────────────────────

export async function finalizeReceive(ctx: Context) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);
  const inTrack    = session.receive?.inTrack;

  await ctx.editMessageReplyMarkup(undefined).catch(() => null);
  await ctx.reply(
    `✅ <b>Приёмка завершена!</b>\n\n` +
    `📦 Трек: <code>${inTrack ?? '—'}</code>\n` +
    `📩 Клиент уведомлён.`,
    { parse_mode: 'HTML', reply_markup: managerMenu() },
  );
  clearSession(telegramId);
}
