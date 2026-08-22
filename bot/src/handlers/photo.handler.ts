// apps/bot/src/handlers/photo.handler.ts
import { Context }  from 'telegraf';
import * as api     from '../api';
import { getSession } from '../session';

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * Обработка фото от менеджера.
 * Если менеджер в процессе приёмки (step = receive:await_photo) —
 * фото загружается к создаваемому заказу.
 * Если orderId передан явно — загружается к конкретному заказу.
 */
export async function handlePhoto(ctx: Context) {
  const telegramId = ctx.from!.id;
  const session    = getSession(telegramId);
  const msg        = ctx.message as any;

  // Берём максимальное разрешение фото
  const photos  = msg?.photo ?? [];
  const largest = photos[photos.length - 1];
  if (!largest) return;

  // Определяем orderId
  let orderId: string | undefined;

  if (session.step === 'receive:await_photo' && session.receive?.orderId) {
    orderId = session.receive.orderId;
  }
  if (session.step === 'manager_order:await_photo' && session.managerOrderPhoto?.orderId) {
    orderId = session.managerOrderPhoto.orderId;
  }

  if (!orderId) {
    await ctx.reply('❌ Неизвестно к какому заказу привязать фото. Используйте /receive');
    return;
  }

  try {
    // Получаем файл из Telegram
    const fileLink = await ctx.telegram.getFileLink(largest.file_id);
    const response = await fetch(fileLink.toString());
    const buffer   = Buffer.from(await response.arrayBuffer());

    await api.uploadPhoto(orderId, buffer, 'image/jpeg');

    const track = session.managerOrderPhoto?.track;
    await ctx.reply(
      `📷 Фото добавлено${track ? ` к заказу <code>${escapeHtml(track)}</code>` : ' к заказу'}.\n` +
      `Можно добавить ещё или нажать «Готово».`,
      {
        parse_mode: 'HTML',
        reply_markup: session.step === 'manager_order:await_photo'
          ? {
            inline_keyboard: [[
              { text: '✅ Готово', callback_data: 'manager_order:photos_done' },
              { text: '❌ Отмена', callback_data: 'cancel' },
            ]],
          }
          : undefined,
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка загрузки фото: ${err.message}`);
  }
}
