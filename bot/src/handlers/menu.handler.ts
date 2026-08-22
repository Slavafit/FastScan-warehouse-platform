// apps/bot/src/handlers/menu.handler.ts

import { Context } from 'telegraf';
import { clientMenu, managerMenu } from '../keyboards';

export async function showManagerMenu(ctx: Context) {
  await ctx.reply(
    '🔧 <b>Меню менеджера</b>',
    { parse_mode: 'HTML', reply_markup: managerMenu() },
  );
}

export async function showClientMenu(ctx: Context) {
  await ctx.reply(
    '👤 <b>Личный кабинет</b>\n\nВыберите раздел:',
    { parse_mode: 'HTML', reply_markup: clientMenu() },
  );
}
