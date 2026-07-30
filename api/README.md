# FastScan API

Собственный REST API платформы FastScan на Node.js, NestJS, Prisma и PostgreSQL. Он сохраняет JSON-поля и маршруты рабочего Android-приложения, а также обслуживает Web и кабинеты клиентов.

Self-hosted FastScan REST API built with Node.js, NestJS, Prisma, and PostgreSQL. It preserves the Android contract and also serves the Web and client accounts.

## Возможности

- JWT access/refresh tokens.
- Роли `ADMIN`, `WORKER`, `CLIENT`.
- Клиенты, заказы, коробки, партии и связи между ними.
- Регистрация клиента по существующему складскому коду.
- Изолированные клиентские заказы, аналитика и уведомления.
- Случайный шестизначный номер клиента и настраиваемый префикс.
- Журнал изменяющих операций.
- OpenAPI JSON и Swagger UI.

Лицензии устройств здесь намеренно отсутствуют: существующая система лицензирования остаётся отдельным сервисом FastScan Mobile.

## Локальный запуск

Проще всего запустить PostgreSQL и API из корня репозитория:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Для разработки API:

```powershell
Copy-Item .env.example .env
npm install
npm run prisma:generate
npm run prisma:migrate
npm run start:dev
```

Переменная `DATABASE_URL` имеет вид:

```text
postgresql://fastscan:fastscan@localhost:5432/fastscan
```

При первом подключении API автоматически создаёт настройку префикса `KG` и администратора из `FASTSCAN_ADMIN_LOGIN` / `FASTSCAN_ADMIN_PASSWORD`.

## Проверка

```powershell
npm run build
npm test -- --runInBand
npm audit --omit=dev
```

- Swagger UI: `http://localhost:8080/swagger-ui`
- OpenAPI JSON: `http://localhost:8080/openapi.json`
- Health check: `http://localhost:8080/health`

Контракты: [Android API](../docs/api-contract.md) и [Client Portal API](../docs/client-portal-api.md).
