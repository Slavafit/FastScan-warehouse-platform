# FastScan Web

React/TypeScript frontend для складских ролей и клиентов FastScan. Интерфейс включает обзор операций, клиентов, заказов, коробок, партий, уведомлений, журнала и настройку префикса клиентского номера.

React/TypeScript frontend for FastScan warehouse roles and clients.

## Запуск

Требуется Node.js 22.

```powershell
Copy-Item .env.example .env.local
npm install
npm run dev
```

Откройте `http://localhost:3000`.

```text
NEXT_PUBLIC_API_URL=http://localhost:8080
```

Для проверки production-сборки:

```powershell
npm run build
npm run start
```

Форма входа использует реальные маршруты NestJS API. Кнопка демонстрационного режима показывает интерфейс без записи данных и удобна до подключения тестового сервера.
