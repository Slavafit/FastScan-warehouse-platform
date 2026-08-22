import { Context } from 'telegraf';
import * as api from '../api';
import { clearSession, getSession, setSession } from '../session';
import { orderStatusLabel } from '../status-labels';
import { handleOrderScan } from './scan.handler';

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

async function ensureManager(ctx: Context): Promise<boolean> {
  const role = getSession(ctx.from!.id).userRole;
  if (role === 'manager' || role === 'admin') return true;
  await ctx.reply('❌ Это действие доступно только менеджеру.');
  return false;
}

export async function showManagerOrders(ctx: Context) {
  if (!await ensureManager(ctx)) return;
  try {
    const orders = await api.api.get('/orders').then(response => response.data as any[]);
    const recent = [...orders]
      .sort((a, b) => {
        const newer = new Date(b.createdAt ?? 0).getTime();
        const older = new Date(a.createdAt ?? 0).getTime();
        return (Number.isNaN(newer) ? 0 : newer) - (Number.isNaN(older) ? 0 : older);
      })
      .slice(0, 10);

    if (!recent.length) {
      await ctx.reply('📋 Заказов пока нет.');
      return;
    }

    await ctx.reply(
      `📋 <b>Последние заказы (${recent.length})</b>\n\n` +
      `Выберите заказ, чтобы открыть карточку и добавить фотографии:`,
      {
        parse_mode: 'HTML',
        reply_markup: {
          inline_keyboard: recent.map(order => [{
            text: `${order.inTrack} · ${orderStatusLabel(order.status)} · 📷 ${order.photos?.length ?? 0}`,
            callback_data: `manager_order:view:${order.id}`,
          }]),
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Не удалось загрузить заказы: ${escapeHtml(err.message)}`, { parse_mode: 'HTML' });
  }
}

export async function showManagerOrder(ctx: Context, orderId: string) {
  if (!await ensureManager(ctx)) return;
  try {
    const order = await api.getOrderById(orderId);
    await handleOrderScan(ctx, order, ctx.from!.id, false);
  } catch (err: any) {
    await ctx.reply(`❌ Не удалось открыть заказ: ${escapeHtml(err.message)}`, { parse_mode: 'HTML' });
  }
}

export async function startManagerOrderPhoto(ctx: Context, orderId: string) {
  if (!await ensureManager(ctx)) return;
  try {
    const order = await api.getOrderById(orderId);
    setSession(ctx.from!.id, {
      step: 'manager_order:await_photo',
      managerOrderPhoto: {
        orderId: order.id,
        track: order.inTrack,
      },
    });
    await ctx.reply(
      `📷 <b>Добавление фото</b>\n\n` +
      `Заказ: <code>${escapeHtml(order.inTrack)}</code>\n\n` +
      `Отправьте одну или несколько фотографий. Все они будут добавлены только к этому заказу.`,
      {
        parse_mode: 'HTML',
        reply_markup: {
          inline_keyboard: [[
            { text: '✅ Готово', callback_data: 'manager_order:photos_done' },
            { text: '❌ Отмена', callback_data: 'cancel' },
          ]],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Не удалось начать загрузку фото: ${escapeHtml(err.message)}`, { parse_mode: 'HTML' });
  }
}

export async function finishManagerOrderPhoto(ctx: Context) {
  if (!await ensureManager(ctx)) return;
  const session = getSession(ctx.from!.id);
  const orderId = session.managerOrderPhoto?.orderId;
  if (!orderId) {
    await ctx.reply('❌ Заказ для загрузки фото не выбран.');
    return;
  }

  clearSession(ctx.from!.id);
  await ctx.reply('✅ Загрузка фотографий завершена.');
  await showManagerOrder(ctx, orderId);
}
