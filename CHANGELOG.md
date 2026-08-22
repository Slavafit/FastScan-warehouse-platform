# Changelog / История изменений

Все значимые изменения FastScan Warehouse Platform фиксируются в этом файле.

All notable changes to FastScan Warehouse Platform are documented in this file. Detailed Android release news remains in [`mobile/app/news.txt`](mobile/app/news.txt).

## [Unreleased]

### Added / Добавлено

- Полный исходный код существующего `@LmsMalagaBot` перенесён в каталог `bot/` монорепозитория с сохранением готовых обработчиков; локальный token остаётся вне Git.
- Безопасное восстановление пароля клиента через привязанный Telegram: одноразовая ссылка действует 15 минут, пароль вводится только в Web, а прежние access/refresh-токены отзываются.
- В профиле клиента добавлены привязка Telegram, выбор предпочтительного канала уведомлений и номер WhatsApp; WhatsApp подготовлен в модели данных и явно отмечен как будущая интеграция.
- Внутренние уведомления теперь сохраняются всегда, а при выборе Telegram дополнительно отправляются привязанному пользователю без влияния на складскую операцию при ошибке внешнего сервиса.
- Добавлен расчёт фактического, объёмного и расчётного веса для заказов и коробок; расчётный вес равен большему из фактического и объёмного, а делитель объёмного веса задаётся администратором (по умолчанию 6000).
- Манифест сохраняет снимок всех трёх весов по заказам и итоговые значения, а клиентский кабинет показывает вес, используемый для будущего расчёта доставки.
- Added a complete API E2E warehouse-flow test covering client and mobile order creation, tracking and client lookup, box packing, batch assignment, and batch completion.
- Добавлена единая оранжевая иконка FastScan с коробкой и рамкой сканера для Web и Android; Debug-вариант использует голубой акцент.
- Android unpacked-order reporting and global order search now use PostgreSQL through `/api/mobile/orders`.
- Android settings now shows automatic sign-in status and a live API readiness indicator with a colored status dot.
- Справочник состава заказа, описание товара и объявленная стоимость; эти данные вместе с контактами клиента сохраняются в манифесте.
- Роль `WORKER` расширена: сотрудник может выполнять складские операции, исправлять данные клиентов и создавать манифесты.
- Манифест завершённой партии: неизменяемый снимок всех заказов из её коробок с клиентскими контактами, общим количеством и весом.
- Административные методы создания и просмотра сотрудников с ролью `WORKER`.
- Admin employee management in Web: create `WORKER` accounts and block or unblock staff access.
- Real client order page in Web with order analytics, tracking search, composition, description, declared value, weight, and box data.
- Admin dictionaries for order composition, warehouses, countries, order statuses, currencies, and packaging types with enable/disable instead of deletion.
- Three-character search and Enter-to-search behavior for client order tracking and admin dictionary screens.
- Real Web pages for boxes and batches: database-backed lists, search, box contents, batch boxes, and batch completion.
- Manifest detail exports from the immutable saved snapshot: print/PDF view, Excel-compatible download, and declared value totals by currency.
- Staff client management: create clients, block or unblock client accounts, reset passwords, and view client order history and analytics.
- Android settings shortcut for switching to the new local test API and clearing old tokens before backend comparison.
- Expanded backend contract tests for the Android compatibility endpoints used by the Retrofit client.
- Modern `/api/mobile/*` backend contract for Android with clear receiver, order, box, and batch endpoints while keeping legacy routes during migration.
- Android Retrofit calls now target the modern `/api/mobile/*` contract while keeping model aliases for a controlled migration.
- Client portal shipment pre-registration by tracking or order number, visible to warehouse/mobile receiving before the parcel arrives.
- Android warehouse receiving now moves pre-registered shipments to `AT_WAREHOUSE` instead of the generic `REGISTERED` status.
- Composition, description, declared value, and currency are documented as Web/client-managed manifest data; Android receiving stays focused on scan, client, dimensions, box flag, and gross weight.
- Android settings now has explicit Back and Save buttons; Save returns to the app after changing required emulator settings.
- Fixed the settings-to-main return flow so Android recreates `MainActivity` and shows fragments after required settings are saved.
- API startup now seeds fixed test accounts for `admin`, `worker`, and `client@test.com` without admin credentials in env files.
- Публичный `GET /health/live` для проверки работающего процесса API независимо от состояния внешних сервисов.
- Публичный `GET /health/ready` для проверки готовности API и соединения с PostgreSQL; при недоступной базе возвращается HTTP `503`.

### Changed / Изменено

- FastScan API публикует адрес `@LmsMalagaBot` для Web даже до завершения переноса обработчиков, поэтому действие Telegram-входа больше не скрывается из-за пустого username.
- На клиентской форме входа ссылка «Забыли пароль?» отображается всегда; при отсутствии Telegram-конфигурации она показывает понятное сообщение вместо скрытия элемента.
- Управление коробками партии переведено на список с галочками и поиском: отмеченные коробки входят в текущую партию, свободные доступны для выбора, а коробки других партий защищены от случайного переноса.
- В карточках коробок партии добавлена явная кнопка удаления с подтверждением; коробка отвязывается от партии, но остаётся в системе.
- Перед печатью этикетки Android запрашивает актуальную партию коробки; API возвращает текущее название партии вместе с данными коробки.
- All API failures now use a safe, consistent response with stable error codes, validation details, request IDs, and clear mappings for authentication, authorization, conflicts, invalid identifiers, and database availability; failed write operations are also recorded in the audit log.
- Clients can request original packaging preservation per order and maintain persistent shoe-box and bag-packaging preferences in their profile; these flags remain visible to Android warehouse staff.
- Box lookup now distinguishes UUIDs from ordinary tracking codes, and the Android launcher icon uses a smaller safe-area mark on a white background.
- Fixed Android batch-list parsing after creation: `guid` and `partial` are now mapped as separate response fields.
- Android Debug now uses `com.ocr.fastscan.dev` and the `FastScan Dev` label, allowing it to be installed alongside the production application.
- Firebase was removed from Android; order registration and packing no longer perform duplicate Firestore writes.
- Боковое меню Web адаптировано для экранов небольшой высоты: список разделов прокручивается, а кнопка выхода остаётся доступной внизу панели.
- `GET /health` теперь показывает версию API, среду запуска, uptime, состояние PostgreSQL и время ответа базы без раскрытия конфиденциальной конфигурации.
- Docker Compose автоматически проверяет готовность API через `/health/ready` и отображает контейнер как `healthy` только при доступной PostgreSQL.
- Клиентский кабинет поддерживает телефон и адрес; код клиента отображается только для чтения и не принимается методом обновления профиля.
- В административные настройки вынесены склад, страна и префикс новых клиентских номеров.
- Web-профиль и настройки склада подключены к реальным API вместо демонстрационных полей.
- Ответ завершения партии теперь содержит `success` и понятное `message`, сохраняя совместимое поле `result` для Mobile.
- Swagger требует Bearer-токен только для защищённых маршрутов; регистрация и вход явно остаются публичными.
- Docker Compose использует единое имя `CORS_ORIGINS` с конфигурацией NestJS.
- Стандартный e2e-тест NestJS заменён проверкой health, входа, PostgreSQL-настройки и защиты маршрутов FastScan.

### Planned / Запланировано

- Подключение WhatsApp-адаптера к уже добавленным настройкам канала и номера клиента.
- Подключение тестовой сборки Android к собственному API.
- Email и push-адаптеры поверх внутреннего центра уведомлений.
- Production deployment, backups, monitoring, and migration from the previous API.

## [0.4.0] — 2026-07-30

### Changed / Изменено

- Основной backend перенесён с экспериментального Kotlin/Spring прототипа на Node.js, NestJS и Prisma.
- Лицензирование устройств исключено из платформенного API: существующий сервер лицензий продолжает обслуживать только Mobile.
- Docker Compose и команды разработки обновлены для NestJS.

### Added / Добавлено

- PostgreSQL schema and initial Prisma migration.
- JWT access/refresh authentication and `ADMIN`, `WORKER`, `CLIENT` roles.
- Android-compatible order, box, batch, receiver, and authentication routes.
- Client registration, profile, isolated orders, analytics, and notifications.
- Random six-digit client numbers and administrator-managed prefix.
- Operation audit log for mutating requests.
- OpenAPI/Swagger documentation.
- React/TypeScript web interface for warehouse and client roles.
- API contract and authentication tests.

### Security

- Production API dependencies pass `npm audit --omit=dev`.
- Client tokens cannot access warehouse routes.
- Administrator routes require the `ADMIN` role.

## [0.3.0] — 2026-07-30

### Added / Добавлено

- Automatic random client-code generation and configurable prefix.
- Uniqueness checks and explicit legacy-code import support.

## [0.2.0] — 2026-07-30

### Added / Добавлено

- Client accounts, analytics, internal notifications, and authorization isolation.

## [0.1.0] — 2026-07-30

### Added / Добавлено

- Monorepo structure with `mobile`, `api`, `web`, and `docs`.
- Initial self-hosted API prototype and PostgreSQL model.
- Existing Android application moved into `mobile`.

[Unreleased]: https://github.com/Slavafit/FastScan-warehouse-platform/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/Slavafit/FastScan-warehouse-platform/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/Slavafit/FastScan-warehouse-platform/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/Slavafit/FastScan-warehouse-platform/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Slavafit/FastScan-warehouse-platform/releases/tag/v0.1.0
