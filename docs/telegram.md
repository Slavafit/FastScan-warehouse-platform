# Telegram в FastScan

Telegram используется как дополнительный канал клиентских уведомлений и для безопасного восстановления пароля. Внутреннее уведомление в личном кабинете сохраняется всегда.

## Настройка

1. Создайте бота через BotFather и получите token и username.
2. Укажите в корневом `.env`:

```dotenv
WEB_APP_URL=https://app.example.com
TELEGRAM_BOT_TOKEN=...
TELEGRAM_BOT_USERNAME=FastScanExampleBot
TELEGRAM_WEBHOOK_SECRET=long-random-secret
```

`WEB_APP_URL` должен открываться у клиента: бот помещает этот адрес в одноразовую ссылку смены пароля. Для webhook нужен публичный HTTPS-адрес API; `localhost` доступен только для разработки интерфейса и не принимается серверами Telegram.

3. Перезапустите API и зарегистрируйте webhook:

```powershell
$botToken = "token-from-botfather"
$publicApiUrl = "https://api.example.com"
$webhookSecret = "the-same-secret-from-env"
Invoke-RestMethod -Method Post -Uri "https://api.telegram.org/bot$botToken/setWebhook" -Body @{
  url = "$publicApiUrl/api/integrations/telegram/webhook"
  secret_token = $webhookSecret
}
```

Токен бота и секрет webhook нельзя добавлять в Git.

## Сценарий клиента

1. В кабинете клиент открывает «Настройки» и нажимает «Привязать Telegram».
2. API выдаёт код на 10 минут; клиент открывает бота по ссылке или отправляет `/link КОД`.
3. После проверки привязки клиент может выбрать Telegram предпочтительным каналом.
4. Для восстановления пароля клиент отправляет боту `/reset`.
5. Бот выдаёт одноразовую Web-ссылку на 15 минут. Новый пароль вводится только на странице FastScan, не в чате.

После смены пароля прежние access- и refresh-токены становятся недействительными. WhatsApp уже предусмотрен в профиле и модели данных, но его отправляющий адаптер пока не подключён, поэтому выбрать этот канал нельзя.
