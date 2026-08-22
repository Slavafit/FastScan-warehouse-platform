import { Context } from 'telegraf';
import * as api from '../api';
import { clearSession, getSession, setSession } from '../session';
import { clientsForDiscountKeyboard, managerMenu } from '../keyboards';
import { userFullName } from '../user-name';

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export async function showDiscountClients(ctx: Context) {
  try {
    const clients = await api.getClients();
    if (!clients.length) {
      await ctx.reply('👥 Клиентов пока нет.');
      return;
    }

    await ctx.reply(
      '💸 <b>Скидки клиентов</b>\n\nВыберите клиента для корректировки скидки:',
      { parse_mode: 'HTML', reply_markup: clientsForDiscountKeyboard(clients) },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка загрузки клиентов: ${err.message}`);
  }
}

export async function startDiscountEdit(ctx: Context, clientId: string) {
  const telegramId = ctx.from!.id;

  try {
    const client = await api.getClient(clientId);
    setSession(telegramId, {
      step: 'manager:await_discount',
      discountEdit: { clientId, clientName: userFullName(client) },
    });

    await ctx.reply(
      `👤 Клиент: <b>${escapeHtml(userFullName(client))}</b>\n` +
      `Текущая скидка: <b>${client.discount ?? 0}%</b>\n\n` +
      'Введите новую скидку числом от 0 до 100.',
      { parse_mode: 'HTML' },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка загрузки клиента: ${err.message}`);
    clearSession(telegramId);
  }
}

export async function handleDiscountInput(ctx: Context, text: string) {
  const telegramId = ctx.from!.id;
  const session = getSession(telegramId);
  const edit = session.discountEdit;

  if (!edit) {
    clearSession(telegramId);
    await ctx.reply('❌ Клиент для изменения скидки не выбран.', { reply_markup: managerMenu() });
    return;
  }

  const discount = Number(text.replace(',', '.'));
  if (!Number.isFinite(discount) || discount < 0 || discount > 100) {
    await ctx.reply('❌ Введите скидку числом от 0 до 100. Например: 10');
    return;
  }

  await ctx.reply(
    `⚠️ <b>Подтвердите изменение скидки</b>\n\n` +
    `Клиент: <b>${escapeHtml(edit.clientName)}</b>\n` +
    `Новая скидка: <b>${discount}%</b>\n\n` +
    `Эта скидка будет применяться к новым начислениям клиента.`,
    {
      parse_mode: 'HTML',
      reply_markup: {
        inline_keyboard: [[
          { text: '✅ Подтвердить', callback_data: `discount_confirm:${edit.clientId}:${discount}` },
          { text: '❌ Отмена',      callback_data: 'cancel' },
        ]],
      },
    },
  );
}

export async function confirmDiscountUpdate(ctx: Context, clientId: string, discount: number) {
  const telegramId = ctx.from!.id;
  const session = getSession(telegramId);

  try {
    await ctx.editMessageText('⏳ Обновляю скидку...');
    const updated = await api.updateClient(clientId, { discount });
    clearSession(telegramId);
    await ctx.reply(
      `✅ Скидка обновлена.\n\n` +
      `Клиент: <b>${escapeHtml(updated ? userFullName(updated) : session.discountEdit?.clientName ?? clientId)}</b>\n` +
      `Скидка: <b>${updated.discount ?? discount}%</b>`,
      { parse_mode: 'HTML', reply_markup: managerMenu() },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка обновления скидки: ${err.message}`);
    clearSession(telegramId);
  }
}
