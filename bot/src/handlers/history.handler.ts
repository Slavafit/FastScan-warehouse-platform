import { Context } from 'telegraf';
import * as api from '../api';

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
    year: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export async function showStatusHistory(ctx: Context) {
  const text = ctx.message && 'text' in ctx.message ? ctx.message.text : '';
  const args = text.trim().split(/\s+/).slice(1);

  if (args.length === 0) {
    await ctx.reply(
      'Введите трек или сущность с ID.\n\n' +
      'Пример: <code>/history ZA123456789</code>\n' +
      'Или: <code>/history order abc123</code>',
      { parse_mode: 'HTML' },
    );
    return;
  }

  try {
    let entityType = args[0];
    let entityId = args[1];
    let title = '';

    if (!entityId) {
      const order = await api.findOrderByTrack(args[0]);
      if (!order) {
        await ctx.reply(`❌ Заказ с треком <code>${escapeHtml(args[0])}</code> не найден.`, {
          parse_mode: 'HTML',
        });
        return;
      }
      entityType = 'order';
      entityId = order.id;
      title = `История заказа <code>${escapeHtml(order.inTrack)}</code>`;
    } else {
      entityType = entityType.toLowerCase();
      title = `История ${escapeHtml(entityType)} <code>${escapeHtml(entityId.slice(-8))}</code>`;
    }

    const history = await api.getStatusHistory(entityType, entityId);
    if (!history.length) {
      await ctx.reply(`📜 ${title}\n\nИстории статусов пока нет.`, { parse_mode: 'HTML' });
      return;
    }

    const lines = history.map((item: any, index: number) => {
      const from = item.oldStatus ?? 'создано';
      const actor = item.changedByRole ? ` · ${item.changedByRole}` : '';
      return `${index + 1}. ${escapeHtml(formatDate(item.createdAt))}\n` +
        `   <code>${escapeHtml(from)}</code> → <code>${escapeHtml(item.newStatus)}</code>${actor}`;
    }).join('\n\n');

    await ctx.reply(`📜 <b>${title}</b>\n\n${lines}`, { parse_mode: 'HTML' });
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка загрузки истории: ${err.message}`);
  }
}
