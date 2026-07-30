# FastScan Mobile API contract

Этот документ фиксирует legacy-контракт рабочего Android-приложения. Новый NestJS API сохраняет пути и JSON-имена во время миграции.

This document records the legacy contract consumed by Android. The NestJS API preserves its paths and JSON names during migration.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/admin/auth/login` | Login with `login` and `password` |
| POST | `/api/admin/auth/refresh` | Exchange `refresh_token` for a token pair |
| GET | `/api/common/reciever?client_code=` | Find receiver by client code |
| GET | `/api/common/all-reciever` | List receivers |
| POST | `/api/common/reciever` | Create or update receiver |
| GET | `/api/order/order-tracking-code?tcode=` | Get an order by tracking code |
| GET | `/api/order/order-tracking-code?boxId=` | List orders in a box |
| GET | `/api/order/order-search?tcode=` | Search an order |
| POST | `/api/order/set-user` | Create or update an order |
| GET | `/api/order/box` | List or filter boxes |
| POST | `/api/order/box` | Create/update a box or attach it to a batch |
| POST | `/api/order/box-order` | Assign an order to a box |
| DELETE | `/api/order/box-order` | Remove an order from a box |
| GET | `/api/order/partial` | List batches |
| POST | `/api/order/partial` | Create or update a batch |
| POST | `/api/order/send-partial` | Complete a batch |
| GET | `/api/order/partial-box?guid=` | List boxes in a batch |
| DELETE | `/api/order/partial-box` | Remove a box from a batch |

Все маршруты, кроме аутентификации, health check и документации, требуют:

```text
Authorization: Bearer <accessToken>
```

OpenAPI JSON: `/openapi.json`; Swagger UI: `/swagger-ui`.
