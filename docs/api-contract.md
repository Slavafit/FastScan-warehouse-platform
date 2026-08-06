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

The compatibility response shapes are covered by `api/src/mobile-contract.spec.ts`.

## Modern mobile API

Android uses `/api/mobile/*`. Legacy `/api/order/*` and
`/api/common/reciever` remain available only as a temporary fallback layer.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/mobile/auth/login` | Login for Android workers |
| POST | `/api/mobile/auth/refresh` | Refresh Android worker tokens |
| GET | `/api/mobile/receivers?client_code=` | Find receiver by client code |
| GET | `/api/mobile/receivers` | List receivers |
| POST | `/api/mobile/receivers` | Create or update receiver |
| GET | `/api/mobile/orders/{trackingNumber}` | Get order by tracking number |
| GET | `/api/mobile/orders?tracking_number=` | Search order by tracking number |
| POST | `/api/mobile/orders` | Create or update an order |
| GET | `/api/mobile/boxes` | List or filter boxes |
| POST | `/api/mobile/boxes` | Create a box |
| PUT | `/api/mobile/boxes/{guid}` | Update box dimensions, weight or tracking code |
| GET | `/api/mobile/boxes/{guid}/orders` | List orders in a box |
| POST | `/api/mobile/boxes/{guid}/orders` | Add order to a box |
| DELETE | `/api/mobile/boxes/{guid}/orders/{orderGuid}` | Remove order from a box |
| POST | `/api/mobile/boxes/orders` | Add order to a box using the current Android request body |
| DELETE | `/api/mobile/boxes/orders` | Remove order from a box using the current Android request body |
| GET | `/api/mobile/batches` | List or filter batches |
| POST | `/api/mobile/batches` | Create a batch |
| PUT | `/api/mobile/batches/{guid}` | Update batch name, code or status |
| POST | `/api/mobile/batches/{guid}/complete` | Complete a batch |
| POST | `/api/mobile/batches/complete` | Complete a batch using the current Android request body |
| GET | `/api/mobile/batches/{guid}/boxes` | List boxes in a batch |
| POST | `/api/mobile/batches/{guid}/boxes` | Add box to a batch |
| DELETE | `/api/mobile/batches/{guid}/boxes/{boxGuid}` | Remove box from a batch |
| GET | `/api/mobile/batches/boxes?guid=` | List boxes in a batch using the current Android query |
| POST | `/api/mobile/batches/boxes` | Add box to a batch using the current Android request body |
| DELETE | `/api/mobile/batches/boxes` | Remove box from a batch using the current Android request body |

The modern response shapes are covered by `api/src/mobile-modern-contract.spec.ts`.
