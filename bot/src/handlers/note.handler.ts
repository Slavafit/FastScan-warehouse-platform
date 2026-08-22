import { Context } from 'telegraf';
import * as api from '../api';
import { clearSession, getSession, setSession } from '../session';

type NoteEntityType = 'order' | 'parcel' | 'box';

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
    day:    '2-digit',
    month:  '2-digit',
    hour:   '2-digit',
    minute: '2-digit',
  });
}

function entityLabel(entityType: string): string {
  const labels: Record<string, string> = {
    order:  'заказу',
    parcel: 'отправлению',
    box:    'боксу',
  };
  return labels[entityType] ?? entityType;
}

function canUseEntityType(value: string): value is NoteEntityType {
  return ['order', 'parcel', 'box'].includes(value);
}

async function getEntityDisplayName(
  ctx: Context,
  entityType: NoteEntityType,
  entityId: string,
): Promise<string> {
  if (entityType !== 'order') {
    return `#${entityId.slice(-8)}`;
  }

  try {
    const session = getSession(ctx.from!.id);
    const order = session.userRole === 'client' && session.userToken
      ? await api.getMyOrderById(session.userToken, entityId)
      : await api.getOrderById(entityId);
    return order.inTrack ? String(order.inTrack) : `#${entityId.slice(-8)}`;
  } catch {
    return `#${entityId.slice(-8)}`;
  }
}

async function loadNotes(ctx: Context, entityType: NoteEntityType, entityId: string) {
  const session = getSession(ctx.from!.id);
  if (session.userRole === 'client' && session.userToken) {
    return api.getMyNotes(session.userToken, entityType, entityId);
  }
  return api.getNotes(entityType, entityId);
}

async function createNote(ctx: Context, entityType: NoteEntityType, entityId: string, text: string) {
  const session = getSession(ctx.from!.id);
  const payload = { entityType, entityId, text };
  if (session.userRole === 'client' && session.userToken) {
    return api.addMyNote(session.userToken, payload);
  }
  return api.addNote(payload);
}

export async function showNotes(ctx: Context, entityType: string, entityId: string) {
  if (!canUseEntityType(entityType)) {
    await ctx.reply('❌ Комментарии доступны для order, parcel или box.');
    return;
  }

  try {
    const [notes, displayName] = await Promise.all([
      loadNotes(ctx, entityType, entityId),
      getEntityDisplayName(ctx, entityType, entityId),
    ]);
    const text = notes.length
      ? notes.slice(-10).map((note: any) =>
        `• ${escapeHtml(formatDate(note.createdAt))} · ${escapeHtml(note.authorRole)}\n` +
        `  ${escapeHtml(note.text)}`
      ).join('\n\n')
      : 'Комментариев пока нет.';

    await ctx.reply(
      `💬 <b>Комментарии к ${entityLabel(entityType)} ${escapeHtml(displayName)}</b>\n\n${text}`,
      {
        parse_mode: 'HTML',
        reply_markup: {
          inline_keyboard: [[
            { text: '➕ Добавить комментарий', callback_data: `note_add:${entityType}:${entityId}` },
          ]],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка загрузки комментариев: ${err.message}`);
  }
}

export async function startNoteInput(ctx: Context, entityType: string, entityId: string) {
  if (!canUseEntityType(entityType)) {
    await ctx.reply('❌ Комментарии доступны для order, parcel или box.');
    return;
  }

  setSession(ctx.from!.id, {
    step: 'note:await_text',
    noteDraft: { entityType, entityId },
  });

  const displayName = await getEntityDisplayName(ctx, entityType, entityId);
  const targetLine = entityType === 'order'
    ? `Заказ: <code>${escapeHtml(displayName)}</code>`
    : `${entityLabel(entityType)}: <code>${escapeHtml(displayName)}</code>`;

  await ctx.reply(
    `💬 <b>Новый комментарий</b>\n\n` +
    `${targetLine}\n\n` +
    `Введите только текст комментария. Трек повторно указывать не нужно.`,
    {
      parse_mode: 'HTML',
      reply_markup: {
        inline_keyboard: [[
          { text: '❌ Отмена', callback_data: 'cancel' },
        ]],
      },
    },
  );
}

export async function handleNoteText(ctx: Context, text: string) {
  const telegramId = ctx.from!.id;
  const session = getSession(telegramId);
  const draft = session.noteDraft;

  if (!draft) {
    await ctx.reply('❌ Не найден объект для комментария. Откройте карточку заново.');
    clearSession(telegramId);
    return;
  }

  const cleanText = text.trim();
  if (!cleanText) {
    await ctx.reply('❌ Комментарий не может быть пустым.');
    return;
  }

  if (cleanText.length > 1000) {
    await ctx.reply('❌ Комментарий слишком длинный. Ограничение: 1000 символов.');
    return;
  }

  try {
    await createNote(ctx, draft.entityType, draft.entityId, cleanText);
    const displayName = await getEntityDisplayName(ctx, draft.entityType, draft.entityId);
    clearSession(telegramId);
    await ctx.reply(
      `✅ Комментарий добавлен к ${entityLabel(draft.entityType)} <code>${escapeHtml(displayName)}</code>.`,
      { parse_mode: 'HTML' },
    );
    await showNotes(ctx, draft.entityType, draft.entityId);
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка сохранения комментария: ${err.message}`);
  }
}
