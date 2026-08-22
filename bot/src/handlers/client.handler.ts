// apps/bot/src/handlers/client.handler.ts
import { Context }   from 'telegraf';
import { clearSession, getSession, setSession } from '../session';
import {
  createMyAddress,
  getApiErrorMessage,
  getMyAddresses,
  updateMyAddress,
  userApi,
} from '../api';
import {
  chargeStatusLabel,
  orderStatusLabel,
  parcelStatusLabel,
  paymentMethodLabel,
} from '../status-labels';
import { clientMenu, clientOrderSkipKeyboard, clientSenderKeyboard } from '../keyboards';
import { userFullName } from '../user-name';
import {
  ENGLISH_ADDRESS_HINT,
  ENGLISH_NAME_HINT,
  isEnglishAddress,
  isEnglishName,
} from '../latin-validation';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function formatMoney(value: unknown, currency = 'EUR'): string {
  const amount = Number(value ?? 0);
  const safeAmount = Number.isFinite(amount) ? amount : 0;
  return `${safeAmount.toFixed(2)} ${currency}`;
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: '2-digit',
  });
}

function parcelNumber(parcel: any): string {
  return parcel?.outTrack
    ? String(parcel.outTrack)
    : `Черновик #${String(parcel?.id ?? '').slice(-6).toUpperCase()}`;
}

export async function showClientProfile(ctx: Context) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) {
    await ctx.reply('❌ Необходима авторизация. Напишите /start');
    return;
  }

  try {
    const user = await userApi(session.userToken).get('/users/me').then(r => r.data);
    await ctx.reply(
      `👤 <b>Мой профиль</b>\n\n` +
      `Имя: <b>${escapeHtml(userFullName(user, '—'))}</b>\n` +
      `Email: ${escapeHtml(user.email || 'не указан')}\n` +
      `Телефон: ${escapeHtml(user.phone || 'не указан')}\n\n` +
      `Email используется для входа и восстановления пароля в веб-кабинете.`,
      {
        parse_mode: 'HTML',
        reply_markup: {
          inline_keyboard: [
            [{ text: '✏️ Изменить имя', callback_data: 'client_profile:name' }],
            [{ text: user.email ? '✏️ Изменить email' : '➕ Добавить email', callback_data: 'client_profile:email' }],
            [{ text: '⬅️ В меню', callback_data: 'client:menu' }],
          ],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка загрузки профиля: ${err.message}`);
  }
}

export async function startClientNameEdit(ctx: Context) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) {
    await ctx.reply('❌ Необходима авторизация. Напишите /start');
    return;
  }
  setSession(ctx.from!.id, { step: 'client:await_name', clientProfileDraft: {} });
  await ctx.reply(`Введите имя английскими буквами.\n${ENGLISH_NAME_HINT}`);
}

export async function handleClientNameInput(ctx: Context, text: string) {
  if (!isEnglishName(text)) {
    await ctx.reply(`❌ Некорректное имя.\n${ENGLISH_NAME_HINT}`);
    return;
  }
  setSession(ctx.from!.id, {
    step: 'client:await_last_name',
    clientProfileDraft: { name: text.trim() },
  });
  await ctx.reply(`Введите фамилию английскими буквами.\n${ENGLISH_NAME_HINT}`);
}

export async function handleClientLastNameInput(ctx: Context, text: string) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken || !session.clientProfileDraft?.name) {
    clearSession(ctx.from!.id);
    await ctx.reply('❌ Сессия редактирования завершена. Откройте профиль снова.');
    return;
  }
  if (!isEnglishName(text)) {
    await ctx.reply(`❌ Некорректная фамилия.\n${ENGLISH_NAME_HINT}`);
    return;
  }
  try {
    await userApi(session.userToken).patch('/users/me', {
      name: session.clientProfileDraft.name,
      lastName: text.trim(),
    });
    clearSession(ctx.from!.id);
    await ctx.reply('✅ Имя и фамилия обновлены.', { reply_markup: clientMenu() });
  } catch (err) {
    await ctx.reply(`❌ ${getApiErrorMessage(err)}`);
  }
}

export async function startClientEmailEdit(ctx: Context) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) {
    await ctx.reply('❌ Необходима авторизация. Напишите /start');
    return;
  }
  setSession(ctx.from!.id, { step: 'client:await_email' });
  await ctx.reply('Введите новый email. Для отмены используйте /cancel.');
}

export async function handleClientEmailInput(ctx: Context, text: string) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) {
    clearSession(ctx.from!.id);
    await ctx.reply('❌ Необходима авторизация. Напишите /start');
    return;
  }

  const email = text.trim().toLowerCase();
  if (!EMAIL_REGEX.test(email)) {
    await ctx.reply('❌ Email введен некорректно. Например: user@example.com');
    return;
  }

  try {
    const updated = await userApi(session.userToken).patch('/users/me', { email }).then(r => r.data);
    clearSession(ctx.from!.id);
    await ctx.reply(
      `✅ Email сохранен: <b>${escapeHtml(updated.email)}</b>\n\nТеперь его можно использовать для входа и восстановления пароля.`,
      { parse_mode: 'HTML', reply_markup: clientMenu() },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Не удалось сохранить email: ${err.message}`);
  }
}

export async function showClientOrders(ctx: Context) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) {
    await ctx.reply('❌ Необходима авторизация. Напишите /start');
    return;
  }
  try {
    const clientApi = userApi(session.userToken);
    const [orders, parcels] = await Promise.all([
      clientApi.get('/orders/my').then(r => r.data),
      clientApi.get('/parcels/my').then(r => r.data),
    ]);
    if (!orders.length) { await ctx.reply('📦 У вас нет заказов.'); return; }
    const parcelsById = new Map(parcels.map((parcel: any) => [parcel.id, parcel]));

    const statusEmoji: Record<string, string> = {
      new: '🆕', expected: '⏳', at_warehouse: '🏭', packed: '📬', shipped: '🚚', delivered: '✅',
    };

    await ctx.reply(
      `📦 <b>Ваши заказы (${orders.length}):</b>`,
      { parse_mode: 'HTML' },
    );

    for (const order of orders.slice(0, 10)) {
      const photos = Array.isArray(order.photos) ? order.photos.filter(Boolean) : [];
      const parcel = order.parcelId ? parcelsById.get(order.parcelId) : null;
      const caption =
        `${statusEmoji[order.status] ?? '📦'} <b>Заказ</b>\n` +
        `Трек: <code>${escapeHtml(order.inTrack)}</code>\n` +
        (parcel ? `Отправление: <code>${escapeHtml(parcelNumber(parcel))}</code>\n` : '') +
        `Отправитель: ${escapeHtml(order.senderName ?? '—')}\n` +
        `Товар: ${escapeHtml(order.itemNameRu ?? '—')}\n` +
        `Вес: ${Number(order.weight) > 0 ? `${order.weight} кг` : 'не указан'}\n` +
        `Статус: <b>${escapeHtml(orderStatusLabel(order.status))}</b>`;

      if (!photos.length) {
        await ctx.reply(caption + '\nФото: пока нет', {
          parse_mode: 'HTML',
          reply_markup: {
            inline_keyboard: [[
              { text: '🧾 Опись', callback_data: `oi:${order.id}` },
              { text: '💬 Комментарии', callback_data: `notes:order:${order.id}` },
            ]],
          },
        });
        continue;
      }

      try {
        await ctx.replyWithPhoto(photos[0], {
          caption,
          parse_mode: 'HTML',
          reply_markup: {
            inline_keyboard: [[
              { text: '🧾 Опись', callback_data: `oi:${order.id}` },
              { text: '💬 Комментарии', callback_data: `notes:order:${order.id}` },
            ]],
          },
        });

        const extraPhotos = photos.slice(1, 10);
        if (extraPhotos.length) {
          await ctx.replyWithMediaGroup(
            extraPhotos.map((url: string) => ({ type: 'photo', media: url })),
          );
        }
      } catch {
        const photoLinks = photos
          .map((url: string, index: number) => `<a href="${escapeHtml(url)}">Фото ${index + 1}</a>`)
          .join(' · ');
        await ctx.reply(`${caption}\n${photoLinks}`, {
          parse_mode: 'HTML',
          reply_markup: {
            inline_keyboard: [[
              { text: '🧾 Опись', callback_data: `oi:${order.id}` },
              { text: '💬 Комментарии', callback_data: `notes:order:${order.id}` },
            ]],
          },
        });
      }
    }
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

export async function startClientOrderRegistration(ctx: Context) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) {
    await ctx.reply('❌ Необходима авторизация. Напишите /start');
    return;
  }
  setSession(ctx.from!.id, {
    step: 'client_order:await_track',
    clientOrderDraft: {},
  });
  await ctx.reply(
    '➕ <b>Новый заказ</b>\n\nВведите трек магазина или курьерской службы:',
    { parse_mode: 'HTML' },
  );
}

export async function handleClientOrderTrack(ctx: Context, text: string) {
  const track = text.trim().toUpperCase();
  if (track.length < 8 || track.length > 30) {
    await ctx.reply('❌ Трек должен содержать от 8 до 30 символов.');
    return;
  }
  if (!/^[A-Z0-9._-]+$/.test(track)) {
    await ctx.reply('❌ Используйте только латинские буквы, цифры и символы «-», «_», «.».');
    return;
  }
  setSession(ctx.from!.id, {
    step: 'client_order:await_sender',
    clientOrderDraft: { inTrack: track },
  });
  const session = getSession(ctx.from!.id);
  const senders = session.userToken
    ? await userApi(session.userToken).get('/senders').then(response => response.data).catch(() => [])
    : [];

  if (!senders.length) {
    await ctx.reply('❌ Список отправителей пока пуст. Обратитесь к менеджеру.');
    return;
  }

  await ctx.reply('Выберите отправителя:', {
    reply_markup: clientSenderKeyboard(senders),
  });
}

export async function handleClientOrderSender(ctx: Context, text: string) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) return;
  const senders = await userApi(session.userToken)
    .get('/senders')
    .then(response => response.data)
    .catch(() => []);

  await ctx.reply('Выберите отправителя кнопкой из списка:', {
    reply_markup: clientSenderKeyboard(senders),
  });
}

export async function handleClientOrderSenderSelect(ctx: Context, senderId: string) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken || session.step !== 'client_order:await_sender') return;

  const sender = await userApi(session.userToken)
    .get(`/senders/${senderId}`)
    .then(response => response.data)
    .catch(() => null);

  if (!sender?.name) {
    await ctx.reply('❌ Отправитель не найден. Выберите другой вариант.');
    return;
  }

  setSession(ctx.from!.id, {
    step: 'client_order:await_weight',
    clientOrderDraft: { ...session.clientOrderDraft, senderName: sender.name },
  });
  await ctx.reply(
    `✅ Отправитель: <b>${escapeHtml(sender.name)}</b>\n\nВведите примерный вес в кг, например: <code>1.25</code>.`,
    {
      parse_mode: 'HTML',
      reply_markup: clientOrderSkipKeyboard('weight'),
    },
  );
}

export async function handleClientOrderWeight(ctx: Context, text: string) {
  const skip = ['пропустить', 'skip', '-', 'нет'].includes(text.trim().toLowerCase());
  const weight = skip ? undefined : Number(text.trim().replace(',', '.'));
  if (!skip && (!Number.isFinite(weight) || Number(weight) <= 0)) {
    await ctx.reply('❌ Вес должен быть больше 0. Например: 1.25');
    return;
  }
  const session = getSession(ctx.from!.id);
  setSession(ctx.from!.id, {
    step: 'client_order:await_dimensions',
    clientOrderDraft: { ...session.clientOrderDraft, weight },
  });
  await ctx.reply(
    'Введите размеры Д × Ш × В в сантиметрах, например: <code>30x20x10</code>.',
    {
      parse_mode: 'HTML',
      reply_markup: clientOrderSkipKeyboard('dimensions'),
    },
  );
}

export async function handleClientOrderDimensions(ctx: Context, text: string) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken || !session.clientOrderDraft) return;
  const skip = ['пропустить', 'skip', '-', 'нет'].includes(text.trim().toLowerCase());
  const values = skip
    ? []
    : text.trim().replace(/,/g, '.').split(/[xх×*;\s]+/i).filter(Boolean).map(Number);
  if (!skip && (values.length !== 3 || values.some(value => !Number.isFinite(value) || value <= 0))) {
    await ctx.reply('❌ Введите три размера в сантиметрах, например: 30x20x10');
    return;
  }

  try {
    const dimensions = skip ? undefined : {
      length: values[0],
      width: values[1],
      height: values[2],
    };
    const order = await userApi(session.userToken).post('/orders/register', {
      inTrack: session.clientOrderDraft.inTrack,
      senderName: session.clientOrderDraft.senderName,
      weight: session.clientOrderDraft.weight,
      dimensions,
    }).then(response => response.data);
    clearSession(ctx.from!.id);
    const weightLine = session.clientOrderDraft.weight
      ? `Вес: ${session.clientOrderDraft.weight} кг\n`
      : 'Вес: не указан\n';
    const dimensionsLine = dimensions
      ? `Размеры: ${values.join(' × ')} см\n`
      : 'Размеры: не указаны\n';
    await ctx.reply(
      `✅ <b>Заказ создан</b>\n\n` +
      `Трек: <code>${escapeHtml(order.inTrack)}</code>\n` +
      `Отправитель: ${escapeHtml(order.senderName)}\n` +
      weightLine +
      dimensionsLine +
      `Статус: <b>новый</b>`,
      { parse_mode: 'HTML', reply_markup: clientMenu() },
    );
  } catch (err) {
    await ctx.reply(`❌ Не удалось создать заказ: ${escapeHtml(getApiErrorMessage(err))}`, { parse_mode: 'HTML' });
  }
}

export async function showClientFinance(ctx: Context) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) {
    await ctx.reply('❌ Необходима авторизация. Напишите /start');
    return;
  }

  try {
    const api = userApi(session.userToken);
    const [summary, charges, payments] = await Promise.all([
      api.get('/finance/summary/my').then(r => r.data),
      api.get('/finance/charges/my').then(r => r.data),
      api.get('/finance/payments/my').then(r => r.data),
    ]);

    const activeCharges = charges
      .filter((charge: any) => charge.status !== 'cancelled')
      .slice(0, 5);

    const chargeLines = activeCharges.length
      ? activeCharges.map((charge: any) => {
        const discount = charge.discountAmount > 0
          ? `\n   Скидка: ${charge.discountPercent}% (${formatMoney(charge.discountAmount, charge.currency)})`
          : '';
        return `• <b>${escapeHtml(charge.title)}</b> · ${escapeHtml(chargeStatusLabel(charge.status))}\n` +
          `   К оплате: ${formatMoney(charge.finalAmount, charge.currency)}\n` +
          `   Оплачено: ${formatMoney(charge.paidAmount, charge.currency)}${discount}`;
      }).join('\n\n')
      : 'Активных начислений нет.';

    const paymentLines = payments.slice(0, 3).length
      ? payments.slice(0, 3).map((payment: any) =>
        `• ${formatDate(payment.createdAt)} · ${formatMoney(payment.amount, payment.currency)} · ${escapeHtml(paymentMethodLabel(payment.method))}`
      ).join('\n')
      : 'Оплат пока нет.';

    const text =
      `💸 <b>Финансы</b>\n\n` +
      `К оплате: <b>${formatMoney(summary.balanceDue, summary.currency)}</b>\n` +
      `Начислено: ${formatMoney(summary.totalCharged, summary.currency)}\n` +
      `Оплачено: ${formatMoney(summary.totalPaid, summary.currency)}\n\n` +
      `Не оплачено: ${summary.unpaidCount}\n` +
      `Частично оплачено: ${summary.partialCount}\n` +
      `Оплачено полностью: ${summary.paidCount}\n\n` +
      `<b>Последние начисления:</b>\n${chargeLines}\n\n` +
      `<b>Последние оплаты:</b>\n${paymentLines}`;

    await ctx.reply(text, { parse_mode: 'HTML' });
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка загрузки финансов: ${err.message}`);
  }
}

export async function showClientParcels(ctx: Context) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) {
    await ctx.reply('❌ Необходима авторизация. Напишите /start');
    return;
  }
  try {
    const api = userApi(session.userToken);
    const parcels = await api.get('/parcels/my').then(r => r.data);

    if (!parcels.length) {
      await ctx.reply('📬 У вас нет отправлений.');
      return;
    }

    const parcelText = parcels.map((p: any) =>
      `📬 <b>${escapeHtml(parcelNumber(p))}</b> · ${escapeHtml(parcelStatusLabel(p.status))}` +
      (p.carrierId ? `\n   Перевозчик: ${escapeHtml(p.carrierId)}` : '') +
      (p.trackingUrl ? `\n   🔗 <a href="${escapeHtml(p.trackingUrl)}">Отследить отправление</a>` : '')
    ).join('\n\n');

    await ctx.reply(`📬 <b>Ваши отправления:</b>\n\n${parcelText}`, {
      parse_mode: 'HTML',
      link_preview_options: { is_disabled: true },
      reply_markup: {
        inline_keyboard: parcels.slice(0, 10).map((parcel: any) => [{
          text: `📬 ${parcelNumber(parcel)}`,
          callback_data: `client_parcel:${parcel.id}`,
        }]),
      },
    });
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

export async function showClientParcel(ctx: Context, parcelId: string) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) {
    await ctx.reply('❌ Необходима авторизация. Напишите /start');
    return;
  }

  try {
    const api = userApi(session.userToken);
    const parcels = await api.get('/parcels/my').then(r => r.data);
    const parcel = parcels.find((item: any) => item.id === parcelId);
    if (!parcel) {
      await ctx.reply('❌ Отправление не найдено.');
      return;
    }

    const orders = await api.get(`/parcels/${parcelId}/orders`).then(r => r.data);
    const dimensions = parcel.dimensions
      ? `${parcel.dimensions.length} × ${parcel.dimensions.width} × ${parcel.dimensions.height} см`
      : 'не указаны';
    const orderLines = orders.length
      ? orders.map((order: any) =>
        `• <code>${escapeHtml(order.inTrack)}</code> · ${escapeHtml(orderStatusLabel(order.status))}`
      ).join('\n')
      : 'Заказов пока нет.';

    await ctx.reply(
      `📬 <b>Отправление</b>\n\n` +
      `Номер: <code>${escapeHtml(parcelNumber(parcel))}</code>\n` +
      `Статус: <b>${escapeHtml(parcelStatusLabel(parcel.status))}</b>\n` +
      `Вес: ${Number(parcel.weight) > 0 ? `${escapeHtml(parcel.weight)} кг` : 'не указан'}\n` +
      `Размеры: ${escapeHtml(dimensions)}\n` +
      (parcel.carrierId ? `Перевозчик: ${escapeHtml(parcel.carrierId)}\n` : '') +
      (parcel.trackingUrl ? `🔗 <a href="${escapeHtml(parcel.trackingUrl)}">Отследить отправление</a>\n` : '') +
      `\n📦 <b>Заказы:</b>\n${orderLines}`,
      {
        parse_mode: 'HTML',
        link_preview_options: { is_disabled: true },
        reply_markup: {
          inline_keyboard: [[
            { text: '💬 Комментарии', callback_data: `notes:parcel:${parcel.id}` },
          ]],
        },
      },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка открытия отправления: ${err.message}`);
  }
}

export async function showClientAddresses(ctx: Context) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) {
    await ctx.reply('❌ Необходима авторизация. Напишите /start');
    return;
  }
  try {
    const addresses = await getMyAddresses(session.userToken);
    if (!addresses.length) {
      await ctx.reply('📍 У вас нет сохранённых адресов.', {
        reply_markup: {
          inline_keyboard: [[
            { text: '➕ Добавить адрес', callback_data: 'client_address:new' },
          ]],
        },
      });
      return;
    }

    const text = addresses.map((a: any) =>
      `${a.isDefault ? '⭐ ' : ''}📍 <b>${a.label}</b>\n` +
      `   ${a.recipient} · ${a.city}, ${a.country}\n` +
      `   ${a.street} ${a.houseNumber ?? ''}${a.building ? `, корп. ${a.building}` : ''}${a.apartment ? `, кв. ${a.apartment}` : ''}, ${a.postalCode}`
    ).join('\n\n');

    await ctx.reply(`📍 <b>Ваши адреса:</b>\n\n${text}`, {
      parse_mode: 'HTML',
      reply_markup: {
        inline_keyboard: [
          ...addresses.map((address: any) => ([{
            text: `✏️ ${address.label}`,
            callback_data: `client_address:edit:${address.id}`,
          }])),
          [{ text: '➕ Добавить адрес', callback_data: 'client_address:new' }],
        ],
      },
    });
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}

export async function startClientAddressEdit(ctx: Context, addressId?: string) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) {
    await ctx.reply('❌ Необходима авторизация. Напишите /start');
    return;
  }

  let draft: any = { label: 'Основной', isDefault: false };
  if (addressId) {
    const addresses = await getMyAddresses(session.userToken);
    const address = addresses.find((item: any) => item.id === addressId);
    if (!address) {
      await ctx.reply('❌ Адрес не найден.');
      return;
    }
    draft = { ...address };
  }

  setSession(ctx.from!.id, {
    step: 'client_address:await_recipient',
    clientAddressDraft: draft,
  });
  await ctx.reply(
    `Введите имя и фамилию получателя английскими буквами.` +
    (draft.recipient ? `\nТекущее значение: ${draft.recipient}` : '') +
    `\n${ENGLISH_NAME_HINT}`,
  );
}

export async function handleClientAddressRecipient(ctx: Context, text: string) {
  if (!isEnglishName(text)) {
    await ctx.reply(`❌ Получатель указан некорректно.\n${ENGLISH_NAME_HINT}`);
    return;
  }
  const session = getSession(ctx.from!.id);
  setSession(ctx.from!.id, {
    step: 'client_address:await_phone',
    clientAddressDraft: { ...session.clientAddressDraft, recipient: text.trim() },
  });
  await ctx.reply('Введите телефон получателя.');
}

export async function handleClientAddressPhone(ctx: Context, text: string) {
  const phone = text.trim();
  if (phone.length < 5) {
    await ctx.reply('❌ Введите корректный телефон получателя.');
    return;
  }
  const session = getSession(ctx.from!.id);
  setSession(ctx.from!.id, {
    step: 'client_address:await_country',
    clientAddressDraft: { ...session.clientAddressDraft, phone },
  });
  await ctx.reply(`Введите страну английскими буквами.\n${ENGLISH_ADDRESS_HINT}`);
}

async function setAddressTextStep(
  ctx: Context,
  text: string,
  field: 'country' | 'city' | 'street' | 'houseNumber',
  nextStep: 'client_address:await_city' | 'client_address:await_postal_code' |
    'client_address:await_house_number' | 'client_address:await_building',
  nextPrompt: string,
) {
  if (!isEnglishAddress(text)) {
    await ctx.reply(`❌ Значение указано некорректно.\n${ENGLISH_ADDRESS_HINT}`);
    return;
  }
  const session = getSession(ctx.from!.id);
  setSession(ctx.from!.id, {
    step: nextStep,
    clientAddressDraft: { ...session.clientAddressDraft, [field]: text.trim() },
  });
  await ctx.reply(nextPrompt);
}

export const handleClientAddressCountry = (ctx: Context, text: string) =>
  setAddressTextStep(ctx, text, 'country', 'client_address:await_city', `Введите город английскими буквами.\n${ENGLISH_ADDRESS_HINT}`);

export const handleClientAddressCity = (ctx: Context, text: string) =>
  setAddressTextStep(ctx, text, 'city', 'client_address:await_postal_code', 'Введите почтовый индекс.');

export async function handleClientAddressPostalCode(ctx: Context, text: string) {
  const postalCode = text.trim();
  if (!/^\d{2,10}$/.test(postalCode)) {
    await ctx.reply('❌ Индекс должен содержать только цифры.');
    return;
  }
  const session = getSession(ctx.from!.id);
  setSession(ctx.from!.id, {
    step: 'client_address:await_street',
    clientAddressDraft: { ...session.clientAddressDraft, postalCode },
  });
  await ctx.reply(`Введите улицу английскими буквами.\n${ENGLISH_ADDRESS_HINT}`);
}

export const handleClientAddressStreet = (ctx: Context, text: string) =>
  setAddressTextStep(ctx, text, 'street', 'client_address:await_house_number', `Введите номер дома.\n${ENGLISH_ADDRESS_HINT}`);

export const handleClientAddressHouseNumber = (ctx: Context, text: string) =>
  setAddressTextStep(ctx, text, 'houseNumber', 'client_address:await_building', 'Введите корпус или напишите «пропустить».');

export async function handleClientAddressBuilding(ctx: Context, text: string) {
  const building = ['пропустить', 'skip', '-'].includes(text.trim().toLowerCase()) ? '' : text.trim();
  if (building && !isEnglishAddress(building)) {
    await ctx.reply(`❌ Корпус указан некорректно.\n${ENGLISH_ADDRESS_HINT}`);
    return;
  }
  const session = getSession(ctx.from!.id);
  setSession(ctx.from!.id, {
    step: 'client_address:await_apartment',
    clientAddressDraft: { ...session.clientAddressDraft, building },
  });
  await ctx.reply('Введите квартиру/офис или напишите «пропустить».');
}

export async function handleClientAddressApartment(ctx: Context, text: string) {
  const apartment = ['пропустить', 'skip', '-'].includes(text.trim().toLowerCase()) ? '' : text.trim();
  if (apartment && !isEnglishAddress(apartment)) {
    await ctx.reply(`❌ Значение указано некорректно.\n${ENGLISH_ADDRESS_HINT}`);
    return;
  }
  const session = getSession(ctx.from!.id);
  setSession(ctx.from!.id, {
    step: 'client_address:await_region',
    clientAddressDraft: { ...session.clientAddressDraft, apartment },
  });
  await ctx.reply('Введите регион английскими буквами или напишите «пропустить».');
}

export async function handleClientAddressRegion(ctx: Context, text: string) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken || !session.clientAddressDraft) return;
  const region = ['пропустить', 'skip', '-'].includes(text.trim().toLowerCase()) ? '' : text.trim();
  if (region && !isEnglishAddress(region)) {
    await ctx.reply(`❌ Регион указан некорректно.\n${ENGLISH_ADDRESS_HINT}`);
    return;
  }

  const draft = { ...session.clientAddressDraft, region };
  const payload = {
    label: draft.label || 'Основной',
    recipient: draft.recipient,
    phone: draft.phone,
    country: draft.country,
    city: draft.city,
    region: draft.region || undefined,
    postalCode: draft.postalCode,
    street: draft.street,
    houseNumber: draft.houseNumber,
    building: draft.building || undefined,
    apartment: draft.apartment || undefined,
    isDefault: draft.isDefault ?? false,
  };

  try {
    if (draft.id) await updateMyAddress(session.userToken, draft.id, payload);
    else await createMyAddress(session.userToken, payload);
    clearSession(ctx.from!.id);
    await ctx.reply('✅ Адрес сохранён.', { reply_markup: clientMenu() });
  } catch (err) {
    await ctx.reply(`❌ ${getApiErrorMessage(err)}`);
  }
}

function statusLine(
  label: string,
  counts: Record<string, number>,
  keys: Array<[string, string]>,
): string {
  const lines = keys
    .map(([key, title]) => `   ${title}: ${counts?.[key] ?? 0}`)
    .join('\n');
  return `${label}\n${lines}`;
}

export async function showClientAnalytics(ctx: Context) {
  const session = getSession(ctx.from!.id);
  if (!session.userToken) {
    await ctx.reply('❌ Необходима авторизация. Напишите /start');
    return;
  }

  try {
    const api = userApi(session.userToken);
    const [analytics, user] = await Promise.all([
      api.get('/analytics/my').then(r => r.data),
      api.get('/users/me').then(r => r.data),
    ]);

    const text =
      `📊 <b>Ваша аналитика</b>\n\n` +
      `💸 <b>Ваша скидка:</b> ${user.discount ?? 0}%\n\n` +
      `📦 <b>Заказы:</b> ${analytics.orders.total}\n` +
      `   Общий вес: ${analytics.orders.totalWeight} кг\n` +
      `   С фото: ${analytics.orders.withPhotos} (${analytics.orders.photoCount} фото)\n` +
      statusLine('   Статусы:', analytics.orders.byStatus, [
        ['new', 'новые'],
        ['expected', 'ожидаются'],
        ['at_warehouse', 'на складе'],
        ['packed', 'упакованы'],
        ['shipped', 'отправлены'],
        ['delivered', 'доставлены'],
      ]) +
      `\n\n📬 <b>Отправления:</b> ${analytics.parcels.total}\n` +
      `   Общий вес: ${analytics.parcels.totalWeight} кг\n` +
      statusLine('   Статусы:', analytics.parcels.byStatus, [
        ['assembling', 'собираются'],
        ['ready', 'готовы'],
        ['in_box', 'готовы к отправке'],
        ['shipped', 'отправлены'],
        ['delivered', 'доставлены'],
      ]);

    await ctx.reply(text, { parse_mode: 'HTML' });
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка: ${err.message}`);
  }
}
