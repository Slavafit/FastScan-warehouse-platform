# FastScan Client Portal API

API личного кабинета использует отдельную роль `CLIENT`. Клиентский JWT не даёт доступа к складским маршрутам `/api/order/**` и `/api/common/**`.

## Регистрация и вход

До регистрации склад создаёт карточку клиента с уникальным `client_code`.

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/client/auth/register` | Public | Register account for an existing code |
| POST | `/api/client/auth/login` | Public | Login with email and password |
| POST | `/api/client/auth/refresh` | Public | Refresh token pair |

```json
{
  "client_code": "KG123456",
  "email": "client@example.com",
  "password": "StrongPassword123"
}
```

На один `client_code` разрешён один кабинет. Пароль хранится как BCrypt hash.

## Личный кабинет

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/client/profile` | Profile and notification preferences |
| PUT | `/api/client/profile` | Update profile and preferences |
| GET | `/api/client/orders` | Authenticated client orders only |
| GET | `/api/client/analytics` | Order, weight, box, status, and unread totals |
| GET | `/api/client/notifications` | List notifications |
| GET | `/api/client/notifications?unreadOnly=true` | List unread notifications |
| PATCH | `/api/client/notifications/{guid}/read` | Mark one as read |
| PATCH | `/api/client/notifications/read-all` | Mark all as read |

Профиль содержит имя, фамилию, телефон, адрес и настройки оповещений. `client_code` и email возвращаются для отображения, но метод `PUT /api/client/profile` не принимает и не изменяет клиентский код.

```json
{
  "first_name": "Anna",
  "last_name": "Client",
  "phone": "+34 600 000 000",
  "address": "Málaga, Spain",
  "email_notifications": true,
  "push_notifications": true
}
```

Требуется `Authorization: Bearer <client-access-token>`.

## Оповещения

Внутренние уведомления создаются при регистрации кабинета, поступлении заказа, упаковке и удалении заказа из коробки. Администратор отправляет сообщение одному клиенту или всем через `POST /api/admin/notifications`. Email и push будут подключаться отдельными адаптерами без изменения клиентского API.
