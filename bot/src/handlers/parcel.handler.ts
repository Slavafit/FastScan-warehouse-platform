// apps/bot/src/handlers/parcel.handler.ts

import { Context }  from 'telegraf';
import * as api     from '../api';
import { getSession, setSession, clearSession } from '../session';
import { clientsKeyboard, parcelsKeyboard } from '../keyboards';
import { userFullName } from '../user-name';
import { parcelStatusLabel } from '../status-labels';

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ── Список открытых Parcel ────────────────────────────────────────────────────

export async function listParcels(ctx: Context) {
  try {
    const parcels = await api.getParcels('assembling');

    if (parcels.length === 0) {
      await ctx.reply(
        '📬 Нет открытых отправлений.\n\nСоздать новое: /parcel_new',
      );
      return;
    }

    const clients = await api.getClients();
    const clientNames = new Map(clients.map((c: any) => [c.id, userFullName(c)]));
    const text = parcels.map((p: any, i: number) => {
      const clientName = clientNames.get(p.clientId) ?? 'клиент не найден';
      return `${i + 1}. #${p.id.slice(-6)} | Клиент: ${clientName}`;
    }).join('\n');

    await ctx.reply(
      `📬 <b>Открытые отправления (${parcels.length}):</b>\n\n${text}\n\n` +
      `Отсканируйте трек посылки чтобы добавить её.`,
      { parse_mode: 'HTML', reply_markup: parcelsKeyboard(parcels) },
    );
  } catch {
    await ctx.reply('❌ Ошибка загрузки отправлений.');
  }
}

// ── Создать новый Parcel ───────────────────────────────────────────────────────

export async function startNewParcel(ctx: Context) {
  const telegramId = ctx.from!.id;
  setSession(telegramId, { step: 'parcel:await_client', parcel: { orderIds: [] } });

  try {
    const clients = await api.getClients();
    await ctx.reply(
      '📬 <b>Новое отправление</b>\n\nВыберите клиента:',
      { parse_mode: 'HTML', reply_markup: clientsKeyboard(clients) },
    );
  } catch {
    await ctx.reply('❌ Ошибка загрузки клиентов.');
    clearSession(telegramId);
  }
}

// ── Выбор клиента для Parcel ──────────────────────────────────────────────────

export async function handleParcelClientSelect(ctx: Context, clientId: string) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);

  try {
    // Создаём Parcel
    const parcel = await api.createParcel(clientId);

    setSession(telegramId, {
      step:   'parcel:await_orders',
      parcel: { ...session.parcel, id: parcel.id, clientId, orderIds: [] },
    });

    // Показываем заказы клиента на складе
    const orders = await api.getClientOrders(clientId);
    const atWarehouse = orders.filter((o: any) => o.status === 'at_warehouse');

    if (atWarehouse.length === 0) {
      await ctx.editMessageText(
        `✅ Отправление #${parcel.id.slice(-6)} создано.\n\n` +
        `У клиента нет заказов на складе.\n` +
        `Отсканируйте треки чтобы добавить посылки.`,
        { parse_mode: 'HTML' },
      );
      return;
    }

    const text = atWarehouse.map((o: any) =>
      `• <code>${o.inTrack}</code> — ${o.itemNameRu ?? '—'} (${o.weight} кг)`
    ).join('\n');

    await ctx.editMessageText(
      `✅ Отправление #${parcel.id.slice(-6)} создано.\n\n` +
      `📦 Заказы клиента на складе:\n${text}\n\n` +
      `Отсканируйте трек посылки чтобы добавить её в отправление.\n` +
      `Или нажмите «Завершить» когда добавите все нужные.`,
      {
        parse_mode:   'HTML',
        reply_markup: {
          inline_keyboard: [[
            { text: '✅ Завершить сборку', callback_data: `parcel_finalize:${parcel.id}` },
            { text: '❌ Отмена',           callback_data: 'cancel' },
          ]],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
    clearSession(telegramId);
  }
}

// ── Добавить Order в Parcel (через скан) ──────────────────────────────────────

export async function addOrderToParcel(
  ctx:      Context,
  parcelId: string,
  orderId:  string,
) {
  try {
    await api.addOrderToParcel(parcelId, [orderId]);
    await ctx.reply(
      `✅ Посылка добавлена в отправление #${parcelId.slice(-6)}`,
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

// ── Завершить сборку Parcel ───────────────────────────────────────────────────

export async function finalizeParcel(ctx: Context, parcelId: string) {
  const telegramId = ctx.from!.id;
  setSession(telegramId, {
    step:   'parcel:await_weight',
    parcel: { id: parcelId, orderIds: [] },
  });

  await ctx.reply(
    `📦 <b>Завершение отправления #${parcelId.slice(-6)}</b>\n\n` +
    `Введите вес запакованной коробки (кг):`,
    { parse_mode: 'HTML' },
  );
}

// ── Ввод веса Parcel ──────────────────────────────────────────────────────────

export async function handleParcelWeight(ctx: Context, text: string) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);
  const weight     = parseFloat(text.replace(',', '.'));

  if (isNaN(weight) || weight <= 0) {
    await ctx.reply('❌ Неверный формат. Введите число, например: <code>2.3</code>', {
      parse_mode: 'HTML',
    });
    return;
  }

  await ctx.reply(
    `⚠️ <b>Подтвердите завершение отправления</b>\n\n` +
    `#${session.parcel!.id!.slice(-6)} | Вес: ${weight} кг\n\n` +
    `После подтверждения клиент получит уведомление, а отправление перейдет в статус ready.`,
    {
      parse_mode: 'HTML',
      reply_markup: {
        inline_keyboard: [[
          { text: '✅ Подтвердить', callback_data: `parcel_confirm_finalize:${session.parcel!.id!}:${weight}` },
          { text: '❌ Отмена',      callback_data: 'cancel' },
        ]],
      },
    },
  );
}

export async function showParcelDetails(ctx: Context, parcelId: string) {
  try {
    const [parcel, orders] = await Promise.all([
      api.api.get(`/parcels/${parcelId}`).then(r => r.data),
      api.api.get(`/parcels/${parcelId}/orders`).then(r => r.data),
    ]);
    const client = await api.getClient(parcel.clientId).catch(() => null);
    const dimensions = parcel.dimensions
      ? `${parcel.dimensions.length} × ${parcel.dimensions.width} × ${parcel.dimensions.height} см`
      : 'не указаны';
    const orderLines = orders.length
      ? orders.map((order: any) =>
        `• <code>${escapeHtml(order.inTrack)}</code> · ${escapeHtml(order.itemNameRu ?? 'без наименования')}`
      ).join('\n')
      : 'Заказов пока нет.';

    await ctx.reply(
      `📬 <b>Отправление</b>\n\n` +
      `Трек: <code>${escapeHtml(parcel.outTrack ?? 'ещё не присвоен')}</code>\n` +
      `Клиент: <b>${escapeHtml(userFullName(client, parcel.clientId.slice(-8)))}</b>\n` +
      `Статус: <b>${escapeHtml(parcelStatusLabel(parcel.status))}</b>\n` +
      `Вес: ${Number(parcel.weight) > 0 ? `${escapeHtml(parcel.weight)} кг` : 'не указан'}\n` +
      `Размеры: ${escapeHtml(dimensions)}\n\n` +
      `📦 <b>Заказы:</b>\n${orderLines}`,
      {
        parse_mode: 'HTML',
        reply_markup: {
          inline_keyboard: [[
            { text: '💬 Комментарии', callback_data: `notes:parcel:${parcel.id}` },
            { text: '👤 Клиент', callback_data: `manager_client:${parcel.clientId}` },
          ]],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка открытия отправления: ${err.message}`);
  }
}

export async function confirmFinalizeParcel(ctx: Context, parcelId: string, weight: number) {
  const telegramId = ctx.from!.id;

  try {
    await ctx.editMessageText('⏳ Завершаю отправление...');
    await api.finalizeParcel(parcelId, {
      weight,
      dimensions: { length: 40, width: 30, height: 20 }, // TODO: запросить у менеджера
    });

    await ctx.reply(
      `✅ <b>Отправление готово!</b>\n\n` +
      `#${parcelId.slice(-6)} | Вес: ${weight} кг\n` +
      `Теперь отправление можно добавить в бокс через /box_list`,
      { parse_mode: 'HTML' },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  } finally {
    clearSession(telegramId);
  }
}
