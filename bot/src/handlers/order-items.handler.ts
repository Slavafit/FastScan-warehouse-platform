import { Context } from 'telegraf';
import {
  addMyOrderItem,
  addOrderItem,
  deleteMyOrderItem,
  deleteOrderItem,
  findOrderByTrack,
  getMyOrderItems,
  getMyOrders,
  getOrderItems,
  searchHsCodes,
  searchMyHsCodes,
  updateMyOrderItem,
  updateOrderItem,
} from '../api';
import { clearSession, getSession, setSession } from '../session';

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function money(value: unknown): string {
  const amount = Number(value ?? 0);
  return `${Number.isFinite(amount) ? amount.toFixed(2) : '0.00'} EUR`;
}

function short(value: string, max = 28): string {
  return value.length > max ? `${value.slice(0, max - 3)}...` : value;
}

function parseQtyPrice(text: string): { quantity: number; unitPrice: number } | null {
  const parts = text.replace(/,/g, '.').trim().split(/\s+/);
  if (parts.length < 2) return null;

  const quantity = Number(parts[0]);
  const unitPrice = Number(parts[1]);
  if (!Number.isFinite(quantity) || quantity <= 0) return null;
  if (!Number.isFinite(unitPrice) || unitPrice <= 0) return null;

  return { quantity, unitPrice };
}

function parseManualHs(text: string) {
  const [itemNameRu, itemNameEn, hsCode] = text.split(';').map(part => part.trim());
  if (!itemNameRu || !itemNameEn || !hsCode) return null;

  return {
    itemNameRu,
    itemNameEn,
    hsCode,
    hsDescription: itemNameEn,
  };
}

function parseEdit(text: string) {
  const [itemNameRu, itemNameEn, hsCode, quantityRaw, unitPriceRaw] =
    text.split(';').map(part => part.trim());

  const quantity = Number((quantityRaw ?? '').replace(',', '.'));
  const unitPrice = Number((unitPriceRaw ?? '').replace(',', '.'));

  if (!itemNameRu || !itemNameEn || !hsCode) return null;
  if (!Number.isFinite(quantity) || quantity <= 0) return null;
  if (!Number.isFinite(unitPrice) || unitPrice <= 0) return null;

  return {
    itemNameRu,
    itemNameEn,
    hsCode,
    hsDescription: itemNameEn,
    quantity,
    unitPrice,
  };
}

function isClient(ctx: Context): boolean {
  return getSession(ctx.from!.id).userRole === 'client';
}

function clientToken(ctx: Context): string | undefined {
  return getSession(ctx.from!.id).userToken;
}

async function loadItems(ctx: Context, orderId: string): Promise<any[]> {
  const token = clientToken(ctx);
  if (isClient(ctx) && token) return getMyOrderItems(token, orderId);
  return getOrderItems(orderId);
}

async function createItem(ctx: Context, orderId: string, data: any): Promise<any> {
  const token = clientToken(ctx);
  if (isClient(ctx) && token) return addMyOrderItem(token, orderId, data);
  return addOrderItem(orderId, data);
}

async function updateItem(ctx: Context, orderId: string, itemId: string, data: any): Promise<any> {
  const token = clientToken(ctx);
  if (isClient(ctx) && token) return updateMyOrderItem(token, orderId, itemId, data);
  return updateOrderItem(orderId, itemId, data);
}

async function removeItem(ctx: Context, orderId: string, itemId: string): Promise<any> {
  const token = clientToken(ctx);
  if (isClient(ctx) && token) return deleteMyOrderItem(token, orderId, itemId);
  return deleteOrderItem(orderId, itemId);
}

async function searchCatalog(ctx: Context, query: string): Promise<any[]> {
  const token = clientToken(ctx);
  if (isClient(ctx) && token) return searchMyHsCodes(token, query);
  return searchHsCodes(query);
}

async function findClientOrder(ctx: Context, query: string): Promise<any | null> {
  const token = clientToken(ctx);
  if (!token) return null;

  const orders = await getMyOrders(token);
  const needle = query.trim().toLowerCase();
  return orders.find((order: any) =>
    String(order.id).toLowerCase() === needle ||
    String(order.id).toLowerCase().endsWith(needle) ||
    String(order.inTrack ?? '').toLowerCase() === needle
  ) ?? null;
}

export async function showOrderItems(ctx: Context, orderId: string) {
  try {
    const items = await loadItems(ctx, orderId);
    const total = items.reduce((sum, item) => sum + Number(item.totalPrice ?? 0), 0);

    const lines = items.length
      ? items.map((item, index) =>
        `${index + 1}. <b>${escapeHtml(item.itemNameRu || item.itemNameEn || 'Позиция')}</b>\n` +
        `   EN: ${escapeHtml(item.itemNameEn || '—')}\n` +
        `   ТН ВЭД: <code>${escapeHtml(item.hsCode || '—')}</code>\n` +
        `   ${escapeHtml(item.quantity)} x ${money(item.unitPrice)} = <b>${money(item.totalPrice)}</b>`
      ).join('\n\n')
      : 'Опись пока пустая.';

    const keyboard = [
      [{ text: '➕ Добавить позицию', callback_data: `oi_add:${orderId}` }],
      ...items.slice(0, 8).flatMap((item: any, index: number) => ([
        [{ text: `✏️ ${index + 1}. ${short(item.itemNameRu || item.itemNameEn || 'Позиция')}`, callback_data: `oi_edit:${orderId}:${item.id}` }],
        [{ text: `Удалить ${index + 1}`, callback_data: `oi_del:${orderId}:${item.id}` }],
      ])),
    ];

    await ctx.reply(
      `🧾 <b>Опись заказа</b>\n` +
      `Заказ: <code>${escapeHtml(orderId.slice(-8))}</code>\n\n` +
      `${lines}\n\n` +
      `Итого для декларации: <b>${money(total)}</b>`,
      { parse_mode: 'HTML', reply_markup: { inline_keyboard: keyboard } },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Не удалось загрузить опись: ${err.message}`);
  }
}

export async function showOrderItemsByCommand(ctx: Context, query: string) {
  if (!query) {
    await ctx.reply(
      'Укажите трек или ID заказа:\n<code>/items TRACK123456</code>',
      { parse_mode: 'HTML' },
    );
    return;
  }

  try {
    const order = isClient(ctx) ? await findClientOrder(ctx, query) : await findOrderByTrack(query);
    if (!order) {
      await ctx.reply('❌ Заказ не найден.');
      return;
    }

    await showOrderItems(ctx, order.id);
  } catch (err: any) {
    await ctx.reply(`❌ Не удалось найти заказ: ${err.message}`);
  }
}

export async function startOrderItemAdd(ctx: Context, orderId: string) {
  setSession(ctx.from!.id, {
    step: 'order_item:await_keyword',
    orderItemDraft: { orderId },
  });

  await ctx.reply(
    'Введите ключевое слово для поиска ТН ВЭД.\n\n' +
    'Например: <code>кроссовки</code>, <code>футболка</code>, <code>сумка</code>.',
    { parse_mode: 'HTML' },
  );
}

export async function handleOrderItemKeyword(ctx: Context, text: string) {
  const session = getSession(ctx.from!.id);
  const draft = session.orderItemDraft;
  if (!draft) return;

  try {
    const results = await searchCatalog(ctx, text);
    const candidate = results[0];

    if (!candidate) {
      setSession(ctx.from!.id, {
        step: 'order_item:await_manual_hs',
        orderItemDraft: { ...draft, keyword: text },
      });
      await ctx.reply(
        'Не нашел подходящий код. Введите данные вручную в формате:\n' +
        '<code>Название RU; Name EN; HS code</code>',
        { parse_mode: 'HTML' },
      );
      return;
    }

    setSession(ctx.from!.id, {
      step: 'order_item:await_keyword',
      orderItemDraft: {
        ...draft,
        keyword: text,
        itemNameRu: candidate.description_ru,
        itemNameEn: candidate.description_en,
        hsCode: candidate.code,
        hsDescription: candidate.description_en,
      },
    });

    await ctx.reply(
      `Нашел вариант:\n\n` +
      `RU: <b>${escapeHtml(candidate.description_ru)}</b>\n` +
      `EN: ${escapeHtml(candidate.description_en)}\n` +
      `ТН ВЭД: <code>${escapeHtml(candidate.code)}</code>\n\n` +
      `Можно подтвердить или исправить вручную.`,
      {
        parse_mode: 'HTML',
        reply_markup: {
          inline_keyboard: [
            [{ text: '✅ Подтвердить', callback_data: 'oi_ok' }],
            [{ text: '✏️ Исправить вручную', callback_data: 'oi_manual' }],
          ],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка поиска ТН ВЭД: ${err.message}`);
  }
}

export async function confirmOrderItemHs(ctx: Context) {
  const session = getSession(ctx.from!.id);
  if (!session.orderItemDraft?.hsCode) {
    await ctx.reply('❌ Сначала введите ключевое слово.');
    return;
  }

  setSession(ctx.from!.id, { step: 'order_item:await_qty_price' });
  await ctx.reply(
    'Введите количество и цену за единицу через пробел.\n\n' +
    'Например: <code>2 45</code>',
    { parse_mode: 'HTML' },
  );
}

export async function startOrderItemManual(ctx: Context) {
  const session = getSession(ctx.from!.id);
  if (!session.orderItemDraft) return;

  setSession(ctx.from!.id, { step: 'order_item:await_manual_hs' });
  await ctx.reply(
    'Введите данные вручную в формате:\n' +
    '<code>Название RU; Name EN; HS code</code>\n\n' +
    'Например: <code>Кроссовки; Sports footwear; 6404110000</code>',
    { parse_mode: 'HTML' },
  );
}

export async function handleOrderItemManual(ctx: Context, text: string) {
  const session = getSession(ctx.from!.id);
  const draft = session.orderItemDraft;
  if (!draft) return;

  const parsed = parseManualHs(text);
  if (!parsed) {
    await ctx.reply(
      'Формат не распознан. Нужно так:\n<code>Название RU; Name EN; HS code</code>',
      { parse_mode: 'HTML' },
    );
    return;
  }

  setSession(ctx.from!.id, {
    step: 'order_item:await_qty_price',
    orderItemDraft: { ...draft, ...parsed },
  });

  await ctx.reply(
    'Отлично. Теперь введите количество и цену за единицу через пробел.\n\n' +
    'Например: <code>2 45</code>',
    { parse_mode: 'HTML' },
  );
}

export async function handleOrderItemQtyPrice(ctx: Context, text: string) {
  const session = getSession(ctx.from!.id);
  const draft = session.orderItemDraft;
  if (!draft) return;

  const parsed = parseQtyPrice(text);
  if (!parsed) {
    await ctx.reply('Введите количество и цену через пробел, например: <code>2 45</code>', { parse_mode: 'HTML' });
    return;
  }

  try {
    await createItem(ctx, draft.orderId, {
      keyword: draft.keyword,
      itemNameRu: draft.itemNameRu,
      itemNameEn: draft.itemNameEn,
      hsCode: draft.hsCode,
      hsDescription: draft.hsDescription,
      quantity: parsed.quantity,
      unitPrice: parsed.unitPrice,
      currency: 'EUR',
    });

    const orderId = draft.orderId;
    clearSession(ctx.from!.id);
    await ctx.reply('✅ Позиция добавлена.');
    await showOrderItems(ctx, orderId);
  } catch (err: any) {
    await ctx.reply(`❌ Не удалось добавить позицию: ${err.response?.data?.message ?? err.message}`);
  }
}

export async function startOrderItemEdit(ctx: Context, orderId: string, itemId: string) {
  setSession(ctx.from!.id, {
    step: 'order_item:await_edit',
    orderItemDraft: { orderId, itemId },
  });

  await ctx.reply(
    'Введите новые данные позиции в формате:\n' +
    '<code>Название RU; Name EN; HS code; количество; цена</code>\n\n' +
    'Например: <code>Кроссовки; Sports footwear; 6404110000; 2; 45</code>',
    { parse_mode: 'HTML' },
  );
}

export async function handleOrderItemEdit(ctx: Context, text: string) {
  const session = getSession(ctx.from!.id);
  const draft = session.orderItemDraft;
  if (!draft?.orderId || !draft.itemId) return;

  const parsed = parseEdit(text);
  if (!parsed) {
    await ctx.reply(
      'Формат не распознан. Нужно так:\n' +
      '<code>Название RU; Name EN; HS code; количество; цена</code>',
      { parse_mode: 'HTML' },
    );
    return;
  }

  try {
    await updateItem(ctx, draft.orderId, draft.itemId, parsed);
    const orderId = draft.orderId;
    clearSession(ctx.from!.id);
    await ctx.reply('✅ Позиция обновлена.');
    await showOrderItems(ctx, orderId);
  } catch (err: any) {
    await ctx.reply(`❌ Не удалось обновить позицию: ${err.response?.data?.message ?? err.message}`);
  }
}

export async function showOrderItemDeleteConfirmation(ctx: Context, orderId: string, itemId: string) {
  await ctx.reply(
    'Удалить позицию из описи?',
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: 'Да, удалить', callback_data: `oi_del_ok:${orderId}:${itemId}` }],
          [{ text: 'Отмена', callback_data: `oi:${orderId}` }],
        ],
      },
    },
  );
}

export async function confirmOrderItemDelete(ctx: Context, orderId: string, itemId: string) {
  try {
    await removeItem(ctx, orderId, itemId);
    await ctx.reply('✅ Позиция удалена.');
    await showOrderItems(ctx, orderId);
  } catch (err: any) {
    await ctx.reply(`❌ Не удалось удалить позицию: ${err.response?.data?.message ?? err.message}`);
  }
}
