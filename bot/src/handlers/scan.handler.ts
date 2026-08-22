import { Context } from 'telegraf';
import * as api from '../api';
import { getSession, setSession } from '../session';
import { boxesKeyboard, parcelsKeyboard } from '../keyboards';
import { activeTrackText, trackHistoryText } from '../shipment-tracks';
import { orderStatusLabel, parcelStatusLabel, boxStatusLabel, shipmentStatusLabel } from '../status-labels';
import { userFullName } from '../user-name';

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

async function getHistoryText(entityType: string, entityId: string, limit = 10): Promise<string> {
  const history = await api.getStatusHistory(entityType, entityId);
  if (!history.length) return 'Истории статусов пока нет.';

  return history.slice(-limit).map((item: any, index: number) => {
    const from = item.oldStatus ?? 'создано';
    const actor = item.changedByRole ? ` · ${item.changedByRole}` : '';
    return `${index + 1}. ${escapeHtml(formatDate(item.createdAt))}\n` +
      `   <code>${escapeHtml(from)}</code> → <code>${escapeHtml(item.newStatus)}</code>${actor}`;
  }).join('\n\n');
}

async function getOrderHistoryPreview(orderId: string): Promise<string> {
  try {
    const text = await getHistoryText('order', orderId, 3);
    return `\n\n📜 <b>История статусов:</b>\n${text}\n` +
      `Полная история: <code>/history order ${escapeHtml(orderId)}</code>`;
  } catch {
    return '';
  }
}

export async function showScannedEntityHistory(
  ctx: Context,
  entityType: string,
  entityId: string,
) {
  try {
    const text = await getHistoryText(entityType, entityId);
    await ctx.reply(
      `📜 <b>История ${escapeHtml(entityType)} #${escapeHtml(entityId.slice(-8))}</b>\n\n${text}`,
      { parse_mode: 'HTML' },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка загрузки истории: ${err.message}`);
  }
}

export async function handleScan(ctx: Context, track: string) {
  const telegramId = ctx.from!.id;
  const isClient = getSession(telegramId).userRole === 'client';

  await ctx.reply(`🔍 Ищу трек <code>${escapeHtml(track)}</code>...`, { parse_mode: 'HTML' });

  try {
    const order = await api.findOrderByTrack(track);
    if (order) {
      await handleOrderScan(ctx, order, telegramId, isClient);
      return;
    }
  } catch {
    // Not an inbound order track.
  }

  try {
    const parcel = await api.findParcelByTrack(track);
    if (parcel) {
      await handleParcelScan(ctx, parcel, isClient);
      return;
    }
  } catch {
    // Not a parcel track.
  }

  if (!isClient) {
    try {
      const shipment = await api.findShipmentByTrack(track);
      if (shipment) {
        await handleShipmentScan(ctx, shipment);
        return;
      }
    } catch {
      // Not an active box shipment track.
    }
  }

  await ctx.reply(
    `❓ Трек <code>${escapeHtml(track)}</code> не найден в системе.\n\n` +
    `Хотите принять эту посылку?\n` +
    `/receive — начать приемку`,
    { parse_mode: 'HTML' },
  );
}

export async function handleOrderScan(ctx: Context, order: any, telegramId: number, isClient: boolean) {
  const statusEmoji: Record<string, string> = {
    new: '🆕',
    expected: '⏳',
    at_warehouse: '📦',
    packed: '📬',
    shipped: '🚚',
    delivered: '✅',
  };

  const [historyPreview, client, parcel, box, trackSummary] = await Promise.all([
    getOrderHistoryPreview(order.id),
    order.clientId ? api.getClient(order.clientId).catch(() => null) : Promise.resolve(null),
    order.parcelId ? api.api.get(`/parcels/${order.parcelId}`).then((r: any) => r.data).catch(() => null) : Promise.resolve(null),
    !isClient && order.boxId ? api.api.get(`/boxes/${order.boxId}`).then((r: any) => r.data).catch(() => null) : Promise.resolve(null),
    !isClient && order.boxId ? api.getShipmentTrackSummary(order.boxId).catch(() => ({ active: null, tracks: [] })) : Promise.resolve({ active: null, tracks: [] }),
  ]);

  const photos = Array.isArray(order.photos) ? order.photos.filter(Boolean) : [];
  const location =
    (parcel ? `\n📬 Отправление: <b>#${escapeHtml(parcel.id.slice(-6))}</b> · ${escapeHtml(parcelStatusLabel(parcel.status))}` : '') +
    (box ? `\n🗃 Бокс: <b>#${escapeHtml(box.id.slice(-6))}</b> · ${escapeHtml(boxStatusLabel(box.status))}` : '') +
    (box ? `\n🚚 Активный трек коробки: ${activeTrackText(trackSummary)}` : '') +
    (box ? `\n📜 История треков:\n${trackHistoryText(trackSummary)}` : '');

  const cardText =
    `${statusEmoji[order.status] ?? '📦'} <b>Заказ найден</b>\n\n` +
    `ID: <code>${escapeHtml(order.id.slice(-8))}</code>\n` +
    `👤 Клиент: <b>${escapeHtml(userFullName(client, order.clientId?.slice(-8) ?? '—'))}</b>\n` +
    `📦 Трек: <code>${escapeHtml(order.inTrack)}</code>\n` +
    `🏷 Отправитель: ${escapeHtml(order.senderName ?? '—')}\n` +
    `📋 Товар: ${escapeHtml(order.itemNameRu ?? '—')}\n` +
    `⚖️ Вес: ${escapeHtml(order.weight)} кг\n` +
    `📷 Фото: ${photos.length}\n` +
    `📊 Статус: <b>${escapeHtml(orderStatusLabel(order.status))}</b>` +
    location +
    historyPreview;

  const quickActions = [
    [
      { text: '📜 История', callback_data: `scan_history:order:${order.id}` },
      { text: '💬 Комментарии', callback_data: `notes:order:${order.id}` },
      { text: '👤 Клиент', callback_data: `manager_client:${order.clientId}` },
    ],
    ...(!isClient ? [[
      { text: '📷 Добавить фото', callback_data: `manager_order:photo:${order.id}` },
    ]] : []),
    ...(!isClient && order.boxId ? [[{ text: '🗃 Открыть бокс', callback_data: `box:${order.boxId}` }]] : []),
  ];

  if (photos.length) {
    try {
      await ctx.replyWithPhoto(photos[0], {
        caption: cardText,
        parse_mode: 'HTML',
        reply_markup: { inline_keyboard: quickActions },
      });
    } catch {
      await ctx.reply(cardText, {
        parse_mode: 'HTML',
        reply_markup: { inline_keyboard: quickActions },
      });
    }
  } else {
    await ctx.reply(cardText, {
      parse_mode: 'HTML',
      reply_markup: { inline_keyboard: quickActions },
    });
  }

  if (order.status === 'at_warehouse') {
    const [parcels, boxes] = await Promise.all([
      api.getParcels('assembling').catch(() => []),
      api.getBoxes('collecting').catch(() => []),
    ]);

    if (parcels.length > 0) {
      setSession(telegramId, {
        step: 'parcel:await_orders',
        parcel: { orderIds: [order.id] },
      });

      await ctx.reply(
        '📬 Быстрое действие: добавить заказ в открытое отправление',
        { reply_markup: parcelsKeyboard(parcels) },
      );
    } else if (boxes.length > 0) {
      setSession(telegramId, {
        step: 'box:await_parcel_or_order',
        box: {},
      });

      await ctx.reply(
        '🗃 Быстрое действие: добавить заказ напрямую в бокс',
        { reply_markup: boxesKeyboard(boxes) },
      );
    } else {
      await ctx.reply(
        '📌 Заказ на складе. Нет открытых отправлений или боксов.\n' +
        'Создайте новое: /parcel_new или /box_new',
      );
    }
  } else if (order.status === 'packed') {
    await ctx.reply('✅ Заказ уже упакован в отправление или бокс.');
  } else if (order.status === 'new' || order.status === 'expected') {
    await ctx.reply('🆕 Заказ зарегистрирован клиентом. Для приемки используйте /receive');
  }
}

async function handleParcelScan(ctx: Context, parcel: any, isClient: boolean) {
  const [client, orders, box, trackSummary] = await Promise.all([
    api.getClient(parcel.clientId).catch(() => null),
    api.api.get(`/parcels/${parcel.id}/orders`).then((r: any) => r.data).catch(() => []),
    !isClient && parcel.boxId ? api.api.get(`/boxes/${parcel.boxId}`).then((r: any) => r.data).catch(() => null) : Promise.resolve(null),
    !isClient && parcel.boxId ? api.getShipmentTrackSummary(parcel.boxId).catch(() => ({ active: null, tracks: [] })) : Promise.resolve({ active: null, tracks: [] }),
  ]);

  const orderLines = orders.slice(0, 5).map((order: any) =>
    `• <code>${escapeHtml(order.inTrack)}</code> · ${escapeHtml(orderStatusLabel(order.status))}`
  ).join('\n') || 'нет заказов';

  await ctx.reply(
    `📬 <b>Отправление найдено</b>\n\n` +
    `ID: <code>${escapeHtml(parcel.id.slice(-8))}</code>\n` +
    `👤 Клиент: <b>${escapeHtml(userFullName(client, parcel.clientId.slice(-8)))}</b>\n` +
    `Статус: <b>${escapeHtml(parcelStatusLabel(parcel.status))}</b>\n` +
    `Вес: ${escapeHtml(parcel.weight ?? '?')} кг\n` +
    (parcel.outTrack ? `Трек: <code>${escapeHtml(parcel.outTrack)}</code>\n` : '') +
    (box ? `🗃 Бокс: <b>#${escapeHtml(box.id.slice(-6))}</b> · ${escapeHtml(boxStatusLabel(box.status))}\n` : '') +
    (box ? `🚚 Активный трек коробки: ${activeTrackText(trackSummary)}\n` : '') +
    (box ? `📜 История треков:\n${trackHistoryText(trackSummary)}\n` : '') +
    `\n📦 <b>Заказы:</b>\n${orderLines}`,
    {
      parse_mode: 'HTML',
      reply_markup: {
        inline_keyboard: [
          [
            { text: '📜 История', callback_data: `scan_history:parcel:${parcel.id}` },
            { text: '💬 Комментарии', callback_data: `notes:parcel:${parcel.id}` },
            { text: '👤 Клиент', callback_data: `manager_client:${parcel.clientId}` },
          ],
          ...(!isClient && parcel.boxId ? [[{ text: '🗃 Открыть бокс', callback_data: `box:${parcel.boxId}` }]] : []),
        ],
      },
    },
  );
}

async function handleShipmentScan(ctx: Context, shipment: any) {
  const [box, contents, trackSummary] = await Promise.all([
    api.api.get(`/boxes/${shipment.boxId}`).then((r: any) => r.data).catch(() => null),
    api.api.get(`/boxes/${shipment.boxId}/contents`).then((r: any) => r.data).catch(() => ({ parcels: [], orders: [] })),
    api.getShipmentTrackSummary(shipment.boxId).catch(() => ({ active: shipment, tracks: [shipment] })),
  ]);

  const clientIds = box?.clientIds ?? [];
  const clientNames = await Promise.all(
    clientIds.slice(0, 5).map((clientId: string) =>
      api.getClient(clientId).then((client: any) => userFullName(client)).catch(() => clientId.slice(-8))
    ),
  );

  await ctx.reply(
    `🚚 <b>Трек коробки найден</b>\n\n` +
    `Трек: <code>${escapeHtml(shipment.trackNumber)}</code>\n` +
    `Активный трек: ${activeTrackText(trackSummary)}\n` +
    `Бокс: <b>#${escapeHtml(shipment.boxId.slice(-6))}</b>\n` +
    `Перевозчик: ${escapeHtml(shipment.carrierId)}\n` +
    `Статус отправки: <b>${escapeHtml(shipmentStatusLabel(shipment.status))}</b>\n` +
    `Статус бокса: <b>${escapeHtml(boxStatusLabel(box?.status))}</b>\n` +
    `Клиенты: ${escapeHtml(clientNames.join(', ') || '—')}\n` +
    `Отправлений в боксе: ${contents.parcels?.length ?? 0}\n` +
    `Прямых заказов в боксе: ${contents.orders?.length ?? 0}\n\n` +
    `📜 <b>История треков:</b>\n${trackHistoryText(trackSummary)}`,
    {
      parse_mode: 'HTML',
      reply_markup: {
        inline_keyboard: [
          [
            { text: '📜 История', callback_data: `scan_history:shipment:${shipment.id}` },
            { text: '💬 Комментарии', callback_data: `notes:box:${shipment.boxId}` },
            { text: '🗃 Открыть бокс', callback_data: `box:${shipment.boxId}` },
          ],
        ],
      },
    },
  );
}
