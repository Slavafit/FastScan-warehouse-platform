# FastScan versions / Версии FastScan

| Component / Компонент | Version / Версия | Status / Статус | Source / Источник |
|---|---:|---|---|
| Platform | `0.4.0` | Development / Разработка | `CHANGELOG.md` |
| FastScan API | `0.4.0` | NestJS MVP | `api/package.json` |
| FastScan Mobile | `4.5 (code 34)` | Working app / Рабочее приложение | `mobile/app/build.gradle` |
| FastScan Web | `0.1.0` | Initial frontend / Первый интерфейс | `web/package.json` |

## Правила версий / Versioning rules

- Platform, API и Web используют Semantic Versioning: `MAJOR.MINOR.PATCH`.
- Mobile сохраняет Android-формат `versionName` и возрастающий `versionCode`.
- Изменения платформы записываются в `CHANGELOG.md`.
- Подробные новости Android продолжают записываться в `mobile/app/news.txt`.
- При выпуске Mobile одновременно обновляются `mobile/app/build.gradle`, этот файл и верхняя запись в `mobile/app/news.txt`.
- При выпуске API или Web одновременно обновляются соответствующий `package.json`, этот файл и `CHANGELOG.md`.
