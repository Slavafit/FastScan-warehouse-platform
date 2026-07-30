# FastScan Pro

**Краткое описание для GitHub:** Android-приложение для складского сканирования заказов, работы с клиентами, коробками и партиями, расчёта веса и печати ZPL-этикеток.

**GitHub description:** Android warehouse app for scanning orders, managing customers, boxes and batches, calculating weight, and printing ZPL labels.

[Русский](#русский) · [English](#english)

---

## Русский

### О приложении

FastScan Pro — Android-приложение для ускорения приёмки, регистрации и упаковки складских заказов. Оно объединяет сканирование трек-кодов и клиентских KG-кодов, получение веса, работу с коробками и партиями, поиск заказов и прямую печать этикеток на сетевом Zebra-совместимом принтере.

Приложение работает напрямую с серверным REST API и рассчитано на внутренний складской процесс.

### Основные возможности

- Сканирование штрихкодов и QR-кодов заказов через CameraX и ML Kit.
- Распознавание клиентских кодов формата `KG…` с помощью OCR.
- Автоматический поиск заказа и клиента по отсканированному коду.
- Поиск клиента по имени и фамилии.
- Получение веса с локального сервера весов.
- Ручной ввод веса и расчёт объёмного веса по размерам заказа.
- Сохранение заказа с клиентом, весом, размерами и параметрами упаковки.
- Глобальный поиск заказа по трек-коду.
- Создание и просмотр открытых или всех коробок.
- Добавление и удаление заказов в коробке.
- Расчёт общего веса как суммы веса заказов и собственного веса пустой коробки.
- Редактирование размеров, веса и трек-кода коробки.
- Создание, редактирование, завершение и восстановление партий.
- Добавление открытых коробок в партию и отправка партии.
- Формирование и отправка текстовых отчётов по партиям и неупакованным заказам.
- Прямая печать ZPL-этикеток заказов и коробок по TCP/IP.
- Информационное предупреждение о большом весе на этикетке коробки.
- Поддержка русского, английского и испанского языков.
- Светлая, тёмная и системная темы.
- Авторизация с access/refresh-токенами и проверка лицензии устройства.
- Звуковые и вибрационные сигналы для основных операций.

### Основные разделы

| Раздел | Назначение |
|---|---|
| Сканирование | Регистрация заказа, поиск клиента, получение веса, ввод размеров и печать этикетки заказа |
| Коробки | Создание коробок, просмотр содержимого, управление заказами, весом и размерами, печать этикетки коробки |
| Партии | Создание и управление партиями, добавление коробок, завершение и отправка отчёта |
| Неупакованные | Поиск заказов, которые ещё не добавлены в коробки, фильтрация и формирование отчёта |
| Настройки | Сервер API, учётные данные, весы, принтер, язык, тема и лицензия |

### Вес коробки

- Собственный вес пустой коробки задаётся в деталях коробки; значение по умолчанию — `0,8 кг`.
- Общий вес рассчитывается как сумма веса всех заказов и веса пустой коробки.
- При сохранении размеров общий вес отправляется на сервер в поле `gross_weight`.
- Если общий вес превышает 25 кг, на этикетке коробки печатается информационное предупреждение `WARNING: HEAVY WEIGHT`.

### Технологии

- Kotlin, Android SDK, View Binding
- CameraX
- Google ML Kit Barcode Scanning и Text Recognition
- Retrofit, OkHttp, Gson
- Kotlin Coroutines
- Firebase Cloud Firestore
- ZXing
- ZPL и печать по TCP-порту `9100`

### Требования

- Android 7.0 или новее (`minSdk 24`)
- Android Studio с JDK 17
- Android SDK 35
- Доступ к рабочему REST API
- Настроенный Firebase-проект
- Камера устройства
- Для дополнительных функций:
  - локальный сервер весов;
  - Zebra-совместимый сетевой принтер с поддержкой ZPL.

### Сборка

1. Клонируйте репозиторий.
2. Откройте проект в Android Studio.
3. Добавьте корректный файл `app/google-services.json` для своего Firebase-проекта.
4. Выполните синхронизацию Gradle.
5. Соберите debug-версию:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
```

Готовый APK будет находиться в:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Настройка

На экране настроек можно указать:

- URL серверного API;
- логин и пароль;
- делитель объёмного веса;
- адрес локального сервера весов;
- IP-адрес и размер этикетки сетевого принтера;
- язык и тему приложения;
- email для проверки лицензии.

Не добавляйте реальные пароли, токены и рабочие конфигурации Firebase в публичный репозиторий.

### Структура проекта

```text
app/src/main/java/com/ocr/fastscan/
├── api/       # Retrofit API и сетевой модуль
├── data/      # Модели, отправка данных и кэш клиентов
├── ui/        # Экраны, адаптеры коробок, партий и заказов
└── utils/     # Печать, лицензия, настройки, Firebase, звук и вспомогательная логика
```

---

## English

### About

FastScan Pro is an Android application designed to speed up warehouse receiving, order registration, and packing. It combines order and customer-code scanning, weight acquisition, box and batch management, order search, and direct label printing to a networked Zebra-compatible printer.

The application communicates directly with a backend REST API and is intended for an internal warehouse workflow.

### Key features

- Scan order barcodes and QR codes with CameraX and ML Kit.
- Recognize `KG…` customer codes using OCR.
- Automatically retrieve order and customer details.
- Search customers by first and last name.
- Read weight from a local scale server.
- Enter weight manually and calculate volumetric weight from order dimensions.
- Save orders with customer, weight, dimensions, and packaging options.
- Search for an order globally by tracking code.
- Create and browse open or archived boxes.
- Add orders to boxes and remove them.
- Calculate total box weight from order weights plus empty-box weight.
- Edit box dimensions, weight, and tracking code.
- Create, rename, complete, and restore batches.
- Add open boxes to a batch and submit completed batches.
- Generate and share text reports for batches and unpacked orders.
- Print order and box labels directly using ZPL over TCP/IP.
- Show an informational heavy-weight warning on box labels.
- Russian, English, and Spanish interface languages.
- Light, dark, and system themes.
- Access/refresh-token authentication and device license validation.
- Sound and vibration feedback for key operations.

### Main sections

| Section | Purpose |
|---|---|
| Scan | Register orders, find customers, retrieve weight, enter dimensions, and print order labels |
| Boxes | Create boxes, manage their orders, weight and dimensions, and print box labels |
| Batches | Create and manage batches, attach boxes, complete batches, and share reports |
| Unpacked | Find orders not yet assigned to boxes, filter them, and generate reports |
| Settings | Configure the API, credentials, scale server, printer, language, theme, and license |

### Box weight

- Empty-box weight is configured in box details and defaults to `0.8 kg`.
- Total weight is calculated from all order weights plus the empty-box weight.
- Saving box dimensions sends the total weight to the server as `gross_weight`.
- When total weight exceeds 25 kg, the box label includes `WARNING: HEAVY WEIGHT`.

### Technology

- Kotlin, Android SDK, View Binding
- CameraX
- Google ML Kit Barcode Scanning and Text Recognition
- Retrofit, OkHttp, Gson
- Kotlin Coroutines
- Firebase Cloud Firestore
- ZXing
- ZPL printing over TCP port `9100`

### Requirements

- Android 7.0 or newer (`minSdk 24`)
- Android Studio with JDK 17
- Android SDK 35
- Access to the backend REST API
- A configured Firebase project
- A device camera
- Optional integrations:
  - local scale server;
  - Zebra-compatible network printer with ZPL support.

### Build

1. Clone the repository.
2. Open the project in Android Studio.
3. Add a valid `app/google-services.json` for your Firebase project.
4. Sync the project with Gradle.
5. Build the debug application:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
```

The generated APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Configuration

The settings screen provides configuration for:

- backend API URL;
- login and password;
- volumetric-weight divisor;
- local scale-server address;
- network-printer IP and label dimensions;
- application language and theme;
- email used for license validation.

Do not commit real passwords, tokens, or production Firebase configuration to a public repository.

### Project structure

```text
app/src/main/java/com/ocr/fastscan/
├── api/       # Retrofit API and network setup
├── data/      # Models, data submission, and customer cache
├── ui/        # Screens and adapters for orders, boxes, and batches
└── utils/     # Printing, licensing, settings, Firebase, sound, and helpers
```
