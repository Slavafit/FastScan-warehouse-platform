// apps/bot/src/handlers/admin.handler.ts

import { Context } from 'telegraf';
import { api }     from '../api';
import { adminMenu, usersForDemoteKeyboard, usersForPromoteKeyboard } from '../keyboards';
import { userFullName } from '../user-name';

// ── Главное меню администратора ───────────────────────────────────────────────

export async function showAdminMenu(ctx: Context) {
  await ctx.reply(
    '🔧 <b>Панель администратора</b>',
    { parse_mode: 'HTML', reply_markup: adminMenu() },
  );
}

// ── Список пользователей ──────────────────────────────────────────────────────

export async function listUsers(ctx: Context) {
  try {
    const users: any[] = await api.get('/users?all=true').then(r => r.data);
    const roleOrder: Record<string, number> = {
      admin: 0,
      manager: 1,
      client: 2,
    };
    const sortedUsers = [...users].sort((a, b) => {
      const roleDifference = (roleOrder[a.role] ?? 99) - (roleOrder[b.role] ?? 99);
      if (roleDifference !== 0) return roleDifference;
      return userFullName(a, 'Пользователь').localeCompare(
        userFullName(b, 'Пользователь'),
        'ru',
      );
    });

    const roleEmoji: Record<string, string> = {
      admin:   '👑',
      manager: '🔧',
      client:  '👤',
    };

    const text = sortedUsers.map(u =>
      `${roleEmoji[u.role] ?? '?'} ${u.telegramId ? '☑️ ' : ''}<b>${userFullName(u, 'Пользователь')}</b> — ${u.role}\n` +
      `   Телефон: ${u.phone ? `<code>${u.phone}</code>` : '—'}`
    ).join('\n\n');

    await ctx.reply(
      `👥 <b>Пользователи (${users.length}):</b>\n` +
      `☑️ Telegram подключен\n\n${text}`,
      {
        parse_mode:   'HTML',
        reply_markup: {
          inline_keyboard: [[
            { text: '⬅️ Назад', callback_data: 'admin:menu' },
          ]],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

// ── Назначить менеджера ───────────────────────────────────────────────────────

export async function showPromoteList(ctx: Context) {
  try {
    const users: any[] = await api.get('/users?all=true').then(r => r.data);
    const clients = users.filter(u => u.role === 'client');

    if (clients.length === 0) {
      await ctx.reply(
        '👤 Нет клиентов для назначения менеджером.',
        {
          reply_markup: {
            inline_keyboard: [[{ text: '⬅️ Назад', callback_data: 'admin:menu' }]],
          },
        },
      );
      return;
    }

    await ctx.reply(
      '👥 Выберите клиента для назначения менеджером:',
      { reply_markup: usersForPromoteKeyboard(clients) },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

export async function promoteToManager(ctx: Context, userId: string) {
  try {
    const user = await api.patch(`/users/${userId}/role`, { role: 'manager' })
      .then(r => r.data);

    await ctx.editMessageText(
      `✅ <b>${userFullName(user, 'Пользователь')}</b> назначен менеджером!`,
      {
        parse_mode:   'HTML',
        reply_markup: {
          inline_keyboard: [[{ text: '⬅️ Назад', callback_data: 'admin:menu' }]],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

export async function showPromoteConfirmation(ctx: Context, userId: string) {
  try {
    const user = await api.get(`/users/${userId}`).then(r => r.data);

    await ctx.editMessageText(
      `⚠️ <b>Подтвердите назначение менеджера</b>\n\n` +
      `Пользователь: <b>${userFullName(user, 'Пользователь')}</b>\n` +
      `Текущая роль: <b>${user.role}</b>\n\n` +
      `После подтверждения пользователь получит доступ к менеджерским действиям.`,
      {
        parse_mode: 'HTML',
        reply_markup: {
          inline_keyboard: [[
            { text: '✅ Назначить', callback_data: `promote_confirm:${userId}` },
            { text: '❌ Отмена',    callback_data: 'admin:promote' },
          ]],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

// ── Снять роль менеджера ─────────────────────────────────────────────────────

export async function showDemoteList(ctx: Context) {
  try {
    const users: any[] = await api.get('/users?all=true').then(r => r.data);
    const managers = users.filter(u => u.role === 'manager');

    if (managers.length === 0) {
      await ctx.reply(
        '🔧 Нет менеджеров, которых можно сделать клиентами.',
        {
          reply_markup: {
            inline_keyboard: [[{ text: '⬅️ Назад', callback_data: 'admin:menu' }]],
          },
        },
      );
      return;
    }

    await ctx.reply(
      '👥 Выберите менеджера, которого нужно сделать клиентом:',
      { reply_markup: usersForDemoteKeyboard(managers) },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

export async function showDemoteConfirmation(ctx: Context, userId: string) {
  try {
    const user = await api.get(`/users/${userId}`).then(r => r.data);

    await ctx.editMessageText(
      `⚠️ <b>Подтвердите снятие роли менеджера</b>\n\n` +
      `Пользователь: <b>${userFullName(user, 'Пользователь')}</b>\n` +
      `Текущая роль: <b>${user.role}</b>\n\n` +
      `После подтверждения пользователь станет обычным клиентом и потеряет доступ к менеджерским действиям.`,
      {
        parse_mode: 'HTML',
        reply_markup: {
          inline_keyboard: [[
            { text: '✅ Сделать клиентом', callback_data: `demote_confirm:${userId}` },
            { text: '❌ Отмена',           callback_data: 'admin:demote' },
          ]],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

export async function demoteToClient(ctx: Context, userId: string) {
  try {
    const user = await api.patch(`/users/${userId}/role`, { role: 'client' })
      .then(r => r.data);

    await ctx.editMessageText(
      `✅ <b>${userFullName(user, 'Пользователь')}</b> теперь клиент.`,
      {
        parse_mode:   'HTML',
        reply_markup: {
          inline_keyboard: [[{ text: '⬅️ Назад', callback_data: 'admin:menu' }]],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

// ── Статистика ────────────────────────────────────────────────────────────────

export async function showStats(ctx: Context) {
  try {
    const [users, orders, boxes] = await Promise.all([
      api.get('/users?all=true').then(r => r.data as any[]),
      api.get('/orders').then(r => r.data as any[]),
      api.get('/boxes').then(r => r.data as any[]),
    ]);

    const managers = users.filter((u: any) => u.role === 'manager').length;
    const clients  = users.filter((u: any) => u.role === 'client').length;

    const ordersByStatus = orders.reduce((acc: any, o: any) => {
      acc[o.status] = (acc[o.status] ?? 0) + 1;
      return acc;
    }, {});

    const boxesByStatus = boxes.reduce((acc: any, b: any) => {
      acc[b.status] = (acc[b.status] ?? 0) + 1;
      return acc;
    }, {});

    await ctx.reply(
      `📊 <b>Статистика</b>\n\n` +
      `👥 Пользователи: ${users.length} (менеджеров: ${managers}, клиентов: ${clients})\n\n` +
      `📦 Заказы: ${orders.length}\n` +
      `  🆕 Новые: ${ordersByStatus.new ?? 0}\n` +
      `  ⏳ Ожидаются: ${ordersByStatus.expected ?? 0}\n` +
      `  🏭 На складе: ${ordersByStatus.at_warehouse ?? 0}\n` +
      `  📬 Упакованы: ${ordersByStatus.packed ?? 0}\n` +
      `  🚚 Отправлены: ${ordersByStatus.shipped ?? 0}\n` +
      `  ✅ Доставлены: ${ordersByStatus.delivered ?? 0}\n\n` +
      `🗃 Боксы: ${boxes.length}\n` +
      `  📦 Собираются: ${boxesByStatus.collecting ?? 0}\n` +
      `  🚚 Отправлены: ${boxesByStatus.shipped ?? 0}\n` +
      `  ✅ Доставлены: ${boxesByStatus.delivered ?? 0}`,
      {
        parse_mode:   'HTML',
        reply_markup: {
          inline_keyboard: [[{ text: '⬅️ Назад', callback_data: 'admin:menu' }]],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}
