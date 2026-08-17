# FastScan Warehouse Platform

План дальнейшей разработки сохранён в [docs/ROADMAP.md](docs/ROADMAP.md).

Платформа для складской приёмки, сканирования и упаковки заказов. Монорепозиторий объединяет рабочее Android-приложение, собственный REST API и веб-кабинеты администратора, сотрудника и клиента.

Warehouse receiving, scanning, and packing platform. The monorepo combines the production Android application, a self-hosted REST API, and web workspaces for administrators, employees, and clients.

## Структура / Structure

```text
FastScan-warehouse-platform/
├── mobile/   # Android scanner application
├── api/      # Node.js + NestJS + Prisma REST API
├── web/      # React + TypeScript web application
└── docs/     # API contracts and architecture notes
```

## Возможности / Features

- Сканирование клиентов, трек-кодов, коробок и партий в FastScan Mobile.
- Совместимые маршруты `/api/order/**`, `/api/common/**` и `/api/admin/**`.
- PostgreSQL, Prisma migrations, JWT access/refresh tokens and role-based access.
- Роли `ADMIN`, `WORKER` и `CLIENT`.
- Регистрация клиентов, личный кабинет, аналитика и внутренние уведомления.
- Автоматический случайный шестизначный номер клиента; префикс задаёт администратор.
- Swagger/OpenAPI documentation and Docker Compose local environment.
- Лицензирование остаётся отдельной существующей системой только для Mobile.

## Быстрый запуск / Quick start

Требуются Docker Desktop и Docker Compose.

```powershell
Copy-Item .env.example .env
docker compose up --build
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui`
- OpenAPI JSON: `http://localhost:8080/openapi.json`
- Detailed health: `http://localhost:8080/health`
- Liveness probe: `http://localhost:8080/health/live`
- Readiness probe: `http://localhost:8080/health/ready`

Web запускается отдельно:

```powershell
cd web
Copy-Item .env.example .env.local
npm install
npm run dev
```

Откройте `http://localhost:3000`. Перед рабочим развёртыванием обязательно замените демонстрационные пароли и JWT secrets.

## Документация / Documentation

- [Версии компонентов](VERSION.md)
- [История изменений](CHANGELOG.md)
- [API](api/README.md)
- [Web](web/README.md)
- [Контракт Android API](docs/api-contract.md)
- [API личного кабинета](docs/client-portal-api.md)
- [История FastScan Mobile](mobile/app/news.txt)
