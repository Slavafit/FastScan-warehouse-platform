import axios from 'axios';
import { Markup } from 'telegraf';

type AccessResponse = {
  login_url: string;
  expires_at: string;
  client_code: string;
  created: boolean;
};

export async function handleFastScanAccess(ctx: any) {
  const apiUrl = (process.env.FASTSCAN_API_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
  const secret = process.env.FASTSCAN_BOT_API_SECRET || process.env.TELEGRAM_BOT_TOKEN;
  if (!secret || !ctx.from || !ctx.chat) {
    await ctx.reply('FastScan API не настроен. Обратитесь к администратору.');
    return;
  }

  try {
    const { data } = await axios.post<AccessResponse>(
      `${apiUrl}/api/integrations/telegram/client-access`,
      {
        telegram_id: String(ctx.from.id),
        chat_id: String(ctx.chat.id),
        username: ctx.from.username,
        first_name: ctx.from.first_name,
        last_name: ctx.from.last_name,
      },
      { headers: { 'x-fastscan-bot-secret': secret }, timeout: 10000 },
    );
    const greeting = data.created
      ? `Ваш кабинет создан. Код клиента: ${data.client_code}.`
      : `Кабинет найден. Код клиента: ${data.client_code}.`;
    await ctx.reply(
      `${greeting}\n\nНажмите кнопку для входа. Ссылка действует 5 минут и используется один раз.`,
      Markup.inlineKeyboard([Markup.button.url('Открыть FastScan', data.login_url)]),
    );
  } catch (error: any) {
    const message = error?.response?.data?.message;
    await ctx.reply(`Не удалось подключиться к FastScan API${message ? `: ${message}` : '.'}`);
  }
}
