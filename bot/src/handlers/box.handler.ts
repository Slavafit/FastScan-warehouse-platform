import { Context } from 'telegraf';
import * as api from '../api';
import { clearSession, getSession, setSession } from '../session';
import { boxesKeyboard, ratesKeyboard } from '../keyboards';
import { boxStatusLabel, orderStatusLabel, parcelStatusLabel } from '../status-labels';
import { activeTrackText, trackHistoryText } from '../shipment-tracks';

type Dimensions = {
  length: number;
  width:  number;
  height: number;
};

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function parsePositiveNumber(text: string): number | null {
  const value = parseFloat(text.replace(',', '.'));
  return Number.isFinite(value) && value > 0 ? value : null;
}

function parseDimensions(text: string): Dimensions | null {
  const values = text
    .replace(/[xх*]/gi, ' ')
    .split(/[\s,;]+/)
    .map(part => parseFloat(part.replace(',', '.')))
    .filter(value => Number.isFinite(value) && value > 0);

  if (values.length !== 3) return null;
  const [length, width, height] = values;
  return { length, width, height };
}

function calcVolumetricWeight(dimensions: Dimensions): number {
  return Number(((dimensions.length * dimensions.width * dimensions.height) / 6000).toFixed(3));
}

function calcBillableWeight(weight: number, dimensions: Dimensions): number {
  return Math.max(weight, calcVolumetricWeight(dimensions));
}

function formatDimensions(dimensions: Dimensions): string {
  return `${dimensions.length} x ${dimensions.width} x ${dimensions.height} см`;
}

function getBoxClientIds(box: any, contents?: { parcels?: any[]; orders?: any[] }): string[] {
  const ids = [
    ...(Array.isArray(box?.clientIds) ? box.clientIds : []),
    ...((contents?.parcels ?? []).map(parcel => parcel.clientId)),
    ...((contents?.orders ?? []).map(order => order.clientId)),
  ].filter(Boolean);

  return [...new Set(ids)];
}

export async function listBoxes(ctx: Context) {
  try {
    const boxes = await api.getBoxes('collecting');

    if (boxes.length === 0) {
      await ctx.reply('🗃 Нет открытых боксов.\n\nСоздать новый: /box_new');
      return;
    }

    await ctx.reply(
      `🗃 <b>Открытые боксы (${boxes.length}):</b>\n\n` +
      `Выберите бокс, чтобы добавить отправление или заказ:`,
      { parse_mode: 'HTML', reply_markup: boxesKeyboard(boxes) },
    );
  } catch {
    await ctx.reply('❌ Ошибка загрузки боксов.');
  }
}

export async function createNewBox(ctx: Context) {
  try {
    const box = await api.createBox();
    await ctx.reply(
      `✅ <b>Бокс создан!</b>\n\n` +
      `🗃 ID: <code>${box.id.slice(-6)}</code>\n\n` +
      `Теперь отсканируйте треки отправлений или заказов, чтобы добавить их в бокс.\n` +
      `Или выберите бокс из списка: /box_list`,
      { parse_mode: 'HTML' },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка создания бокса: ${err.message}`);
  }
}

export async function addParcelToBox(ctx: Context, boxId: string, parcelId: string) {
  try {
    await api.addToBox(boxId, { parcelId });
    await ctx.reply(
      `✅ Отправление #${parcelId.slice(-6)} добавлено в бокс #${boxId.slice(-6)}`,
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

export async function showBoxContents(ctx: Context, boxId: string) {
  try {
    const [box, contents, trackSummary] = await Promise.all([
      api.getBox(boxId),
      api.getBoxContents(boxId),
      api.getShipmentTrackSummary(boxId).catch(() => ({ active: null, tracks: [] })),
    ]);
    const { parcels, orders } = contents;

    const parcelList = parcels.map((p: any) =>
      `  📬 Отправление #${p.id.slice(-6)} · ${escapeHtml(parcelStatusLabel(p.status))} · ${p.weight ?? '?'} кг`
    ).join('\n');

    const orderList = orders.map((o: any) =>
      `  📦 ${escapeHtml(o.inTrack)} · ${escapeHtml(orderStatusLabel(o.status))} · ${o.weight} кг`
    ).join('\n');

    const boxDetails = [
      `Статус: <b>${escapeHtml(boxStatusLabel(box.status))}</b>`,
      box.weight ? `Вес: <b>${box.weight} кг</b>` : '',
      box.dimensions ? `Размеры: <b>${escapeHtml(formatDimensions(box.dimensions))}</b>` : '',
      box.volumetricWeight ? `Объемный вес: <b>${box.volumetricWeight} кг</b>` : '',
      `Активный трек: ${activeTrackText(trackSummary)}`,
    ].filter(Boolean).join('\n');

    const text =
      `🗃 <b>Бокс #${boxId.slice(-6)}</b>\n${boxDetails}\n\n` +
      `🚚 <b>История треков:</b>\n${trackHistoryText(trackSummary)}\n\n` +
      (parcelList ? `📬 Отправления:\n${parcelList}\n\n` : '') +
      (orderList  ? `📦 Прямые заказы:\n${orderList}\n\n` : '') +
      (!parcelList && !orderList ? '(пусто)\n\n' : '');

    await ctx.reply(text, {
      parse_mode:   'HTML',
      reply_markup: {
        inline_keyboard: [[
          { text: '🚀 Закрыть и выбрать тариф', callback_data: `box_finalize:${boxId}` },
          { text: '💬 Комментарии', callback_data: `notes:box:${boxId}` },
        ]],
      },
    });
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

export async function finalizeAndShipBox(ctx: Context, boxId: string) {
  try {
    const box = await api.getBox(boxId);

    if (box.status === 'ready_to_ship') {
      await showRatesForBox(ctx, boxId);
      return;
    }

    if (box.status !== 'collecting') {
      await ctx.reply(
        `❌ Бокс #${boxId.slice(-6)} уже нельзя закрыть. ` +
        `Текущий статус: ${escapeHtml(boxStatusLabel(box.status))}`,
      );
      return;
    }

    setSession(ctx.from!.id, {
      step: 'box:await_weight',
      box:  { id: boxId },
    });

    await ctx.reply(
      `🗃 <b>Закрытие бокса #${boxId.slice(-6)}</b>\n\n` +
      `Введите фактический вес коробки в кг.\nНапример: <code>7.4</code>`,
      { parse_mode: 'HTML' },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка закрытия бокса: ${err.message}`);
  }
}

export async function handleBoxWeightInput(ctx: Context, text: string) {
  const weight = parsePositiveNumber(text);
  if (!weight) {
    await ctx.reply('❌ Введите вес числом в кг. Например: 7.4');
    return;
  }

  const session = getSession(ctx.from!.id);
  if (!session.box?.id) {
    await ctx.reply('❌ Бокс не найден в сессии. Откройте бокс заново.');
    return;
  }

  setSession(ctx.from!.id, {
    step: 'box:await_dimensions',
    box:  { ...session.box, weight },
  });

  await ctx.reply(
    `✅ Вес: <b>${weight} кг</b>\n\n` +
    `Введите размеры коробки в сантиметрах: длина ширина высота.\n` +
    `Например: <code>60 40 35</code>`,
    { parse_mode: 'HTML' },
  );
}

export async function handleBoxDimensionsInput(ctx: Context, text: string) {
  const dimensions = parseDimensions(text);
  if (!dimensions) {
    await ctx.reply('❌ Введите три размера в см. Например: 60 40 35');
    return;
  }

  const session = getSession(ctx.from!.id);
  if (!session.box?.id || !session.box.weight) {
    await ctx.reply('❌ Не найден вес или бокс в сессии. Начните закрытие бокса заново.');
    return;
  }

  setSession(ctx.from!.id, {
    step: 'box:await_address',
    box:  { ...session.box, dimensions },
  });

  try {
    const [box, contents] = await Promise.all([
      api.getBox(session.box.id),
      api.getBoxContents(session.box.id),
    ]);
    const clientIds = getBoxClientIds(box, contents);

    if (!clientIds.length) {
      await ctx.reply('❌ В боксе нет клиента. Добавьте отправления или заказы перед закрытием.');
      return;
    }

    if (clientIds.length > 1) {
      await ctx.reply(
        '⚠️ В боксе несколько клиентов. Для отправки нужен один адрес. Сейчас покажу адреса первого клиента в боксе.',
      );
    }

    const addresses = await api.getClientAddresses(clientIds[0]);
    if (!addresses.length) {
      await ctx.reply('❌ У клиента нет адресов доставки. Добавьте адрес и повторите закрытие бокса.');
      return;
    }

    await ctx.reply(
      `📐 Размеры: <b>${escapeHtml(formatDimensions(dimensions))}</b>\n` +
      `Вес к расчету: <b>${calcBillableWeight(session.box.weight, dimensions)} кг</b>\n\n` +
      `Выберите адрес доставки:`,
      {
        parse_mode: 'HTML',
        reply_markup: {
          inline_keyboard: [
            ...addresses.map((address: any) => ([{
              text: `${address.isDefault ? '⭐ ' : ''}${address.label} · ${address.city}, ${address.country}`,
              callback_data: `box_address:${address.id}`,
            }])),
            [{ text: '❌ Отмена', callback_data: 'cancel' }],
          ],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка загрузки адресов: ${err.message}`);
  }
}

export async function handleBoxAddressSelect(ctx: Context, addressId: string) {
  const session = getSession(ctx.from!.id);
  const boxId = session.box?.id;
  const weight = session.box?.weight;
  const dimensions = session.box?.dimensions;

  if (!boxId || !weight || !dimensions) {
    await ctx.reply('❌ Не найдены данные бокса. Начните закрытие бокса заново.');
    return;
  }

  try {
    await api.finalizeBox(boxId, { weight, dimensions, addressId });
    clearSession(ctx.from!.id);

    await ctx.reply(
      `✅ <b>Бокс закрыт</b>\n\n` +
      `Бокс: <b>#${boxId.slice(-6)}</b>\n` +
      `Фактический вес: <b>${weight} кг</b>\n` +
      `Размеры: <b>${escapeHtml(formatDimensions(dimensions))}</b>\n` +
      `Объемный вес: <b>${calcVolumetricWeight(dimensions)} кг</b>\n` +
      `Вес к расчету: <b>${calcBillableWeight(weight, dimensions)} кг</b>`,
      { parse_mode: 'HTML' },
    );

    await showRatesForBox(ctx, boxId);
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка финализации бокса: ${err.message}`);
  }
}

export async function confirmShipment(
  ctx:         Context,
  boxId:       string,
  serviceCode: string,
  carrierId:   string,
  price:       number,
) {
  try {
    await ctx.editMessageText('⏳ Оформляю отправку...');

    const result = await api.shipBox(boxId, {
      serviceCode,
      carrierId,
      shippingCost: price,
    });

    await ctx.reply(
      `✅ <b>Бокс отправлен!</b>\n\n` +
      `🗃 Бокс: #${boxId.slice(-6)}\n` +
      `📮 Трек: <code>${result.trackNumber}</code>\n` +
      `🚚 Перевозчик: ${escapeHtml(carrierId)}\n` +
      `💶 Стоимость: €${price}`,
      { parse_mode: 'HTML' },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка оформления: ${err.message}`);
  }
}

export async function showShipmentConfirmation(
  ctx:         Context,
  boxId:       string,
  serviceCode: string,
  carrierId:   string,
  price:       number,
) {
  await ctx.editMessageText(
    `⚠️ <b>Подтвердите отправку бокса</b>\n\n` +
    `Бокс: <b>#${boxId.slice(-6)}</b>\n` +
    `Перевозчик: <b>${escapeHtml(carrierId)}</b>\n` +
    `Стоимость: <b>€${price}</b>\n\n` +
    `После подтверждения будет создан внутренний трек бокса.`,
    {
      parse_mode: 'HTML',
      reply_markup: {
        inline_keyboard: [[
          { text: '✅ Отправить', callback_data: `ship_confirm:${boxId}:${serviceCode}:${carrierId}:${price}` },
          { text: '❌ Отмена',   callback_data: 'cancel' },
        ]],
      },
    },
  );
}

async function showRatesForBox(ctx: Context, boxId: string) {
  await ctx.reply('⏳ Запрашиваю тарифы...');
  const [box, rates] = await Promise.all([
    api.getBox(boxId),
    api.getRatesForBox(boxId),
  ]);

  if (!rates || rates.length === 0) {
    await ctx.reply('❌ Тарифы не найдены. Проверьте адрес, вес и размеры бокса.');
    return;
  }

  const billableWeight = box.weight && box.dimensions
    ? calcBillableWeight(box.weight, box.dimensions)
    : box.weight ?? '?';

  await ctx.reply(
    `🚚 <b>Выберите тариф для бокса #${boxId.slice(-6)}</b>\n\n` +
    `Фактический вес: <b>${box.weight ?? '?'} кг</b>\n` +
    `Объемный вес: <b>${box.volumetricWeight ?? '?'} кг</b>\n` +
    `Вес к расчету: <b>${billableWeight} кг</b>`,
    { parse_mode: 'HTML', reply_markup: ratesKeyboard(rates) },
  );
}
