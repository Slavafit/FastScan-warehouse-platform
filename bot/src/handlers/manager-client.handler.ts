import { Context } from 'telegraf';
import * as api from '../api';
import { clientsForManagerKeyboard, managerMenu } from '../keyboards';
import { orderStatusLabel } from '../status-labels';
import { userFullName } from '../user-name';

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function countByStatus(items: any[]): Record<string, number> {
  return items.reduce((acc: Record<string, number>, item: any) => {
    acc[item.status] = (acc[item.status] ?? 0) + 1;
    return acc;
  }, {});
}

function formatMoney(value: unknown, currency = 'EUR'): string {
  const amount = Number(value ?? 0);
  const safeAmount = Number.isFinite(amount) ? amount : 0;
  return `${safeAmount.toFixed(2)} ${currency}`;
}

function statusSummary(counts: Record<string, number>, labels: Record<string, string>): string {
  const lines = Object.entries(labels)
    .map(([status, label]) => `${label}: ${counts[status] ?? 0}`)
    .join(' · ');
  return lines || 'нет данных';
}

export async function showManagerClients(ctx: Context) {
  try {
    const clients = await api.getClients();
    if (!clients.length) {
      await ctx.reply('👤 Клиентов пока нет.', { reply_markup: managerMenu() });
      return;
    }

    await ctx.reply(
      `👤 <b>Клиенты (${clients.length})</b>\n\nВыберите клиента, чтобы открыть карточку:`,
      { parse_mode: 'HTML', reply_markup: clientsForManagerKeyboard(clients) },
    );
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка загрузки клиентов: ${err.message}`);
  }
}

export async function showManagerClientCard(ctx: Context, clientId: string) {
  try {
    const [client, orders, parcels, addresses, finance, boxes] = await Promise.all([
      api.getClient(clientId),
      api.getClientOrders(clientId).catch(() => []),
      api.api.get(`/parcels/client/${clientId}`).then((r: any) => r.data).catch(() => []),
      api.getClientAddresses(clientId).catch(() => []),
      api.api.get(`/finance/summary/client/${clientId}`).then((r: any) => r.data).catch(() => null),
      api.api.get('/boxes').then((r: any) => r.data as any[]).catch(() => []),
    ]);

    const clientBoxes = boxes.filter((box: any) =>
      Array.isArray(box.clientIds) && box.clientIds.includes(clientId)
    );

    const activeBoxTracks = await Promise.all(
      clientBoxes
        .filter((box: any) => ['ready_to_ship', 'shipped'].includes(box.status))
        .slice(0, 3)
        .map(async (box: any) => {
          try {
            const shipment = await api.api.get(`/shipments/box/${box.id}/active`).then((r: any) => r.data);
            return shipment?.trackNumber
              ? `#${box.id.slice(-6)}: <code>${escapeHtml(shipment.trackNumber)}</code>`
              : `#${box.id.slice(-6)}: трека нет`;
          } catch {
            return `#${box.id.slice(-6)}: трека нет`;
          }
        }),
    );

    const orderCounts = countByStatus(orders);
    const parcelCounts = countByStatus(parcels);
    const boxCounts = countByStatus(clientBoxes);
    const defaultAddress = addresses.find((address: any) => address.isDefault) ?? addresses[0];
    const addressLine = defaultAddress
      ? [
        defaultAddress.country,
        defaultAddress.city,
        defaultAddress.street,
        defaultAddress.houseNumber,
        defaultAddress.building,
        defaultAddress.apartment,
      ].filter(Boolean).join(', ')
      : 'нет адреса';

    const recentOrders = orders.slice(0, 3).map((order: any) =>
      `• <code>${escapeHtml(order.inTrack)}</code> · ${escapeHtml(orderStatusLabel(order.status))}`
    ).join('\n') || 'нет заказов';

    const text =
      `👤 <b>${escapeHtml(userFullName(client))}</b>\n` +
      `Роль: <b>${escapeHtml(client.role)}</b>\n` +
      `Скидка: <b>${client.discount ?? 0}%</b>\n` +
      `Email: ${escapeHtml(client.email ?? '—')}\n` +
      `Телефон: ${escapeHtml(client.phone ?? '—')}\n\n` +
      `💸 <b>Финансы:</b> ${finance ? formatMoney(finance.balanceDue, finance.currency) + ' к оплате' : 'нет данных'}\n\n` +
      `📦 <b>Заказы:</b> ${orders.length}\n` +
      `${statusSummary(orderCounts, {
        new: 'новые',
        expected: 'ожидаются',
        at_warehouse: 'на складе',
        packed: 'упакованы',
        shipped: 'отправлены',
        delivered: 'доставлены',
      })}\n\n` +
      `📬 <b>Отправления:</b> ${parcels.length}\n` +
      `${statusSummary(parcelCounts, {
        assembling: 'собираются',
        ready: 'готовы',
        in_box: 'в боксе',
        shipped: 'отправлены',
        delivered: 'доставлены',
      })}\n\n` +
      `🗃 <b>Боксы:</b> ${clientBoxes.length}\n` +
      `${statusSummary(boxCounts, {
        collecting: 'собираются',
        ready_to_ship: 'готовы',
        shipped: 'отправлены',
        delivered: 'доставлены',
      })}\n\n` +
      `🚚 <b>Активные треки:</b>\n${activeBoxTracks.join('\n') || 'нет активных треков'}\n\n` +
      `📍 <b>Адрес:</b> ${escapeHtml(addressLine)}\n\n` +
      `🕓 <b>Последние заказы:</b>\n${recentOrders}`;

    await ctx.reply(text, {
      parse_mode: 'HTML',
      reply_markup: {
        inline_keyboard: [
          [
            { text: '💸 Изменить скидку', callback_data: `discount_client:${client.id}` },
            { text: '📬 Новое отправление', callback_data: 'parcel_new' },
          ],
          [
            { text: '👤 К списку клиентов', callback_data: 'manager:clients' },
            { text: '🔧 Меню', callback_data: 'switch:manager' },
          ],
        ],
      },
    });
  } catch (err: any) {
    await ctx.reply(`❌ Ошибка загрузки карточки клиента: ${err.message}`);
  }
}
