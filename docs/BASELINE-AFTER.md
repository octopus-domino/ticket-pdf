# CoDoc Phase 1.1 — Regression baseline

Исходные SHA и результаты сверены с [BASELINE Phase 1](architecture-evidence/BASELINE.md). Таблица сравнивает одинаковые области. Обозначения в колонке «Тесты» идут в порядке passed / failed / skipped.

| Проверка | Phase 1 | Phase 1.1 | Доказательство |
| --- | --- | --- | --- |
| Editor `npm run check` | 5 TS2769, product typecheck проходит | **PASS**, 0 ошибок TypeScript | `editor-type-before.log`, `editor-check-final-sources.log`. Проверены product, test и E2E. После изменения RSVP-fixture E2E typecheck повторно прошёл. |
| Editor `npm run lint:strict` | 3 предупреждения, лимит 0 | **PASS**, 0 предупреждений | `editor-lint-before.log`, `editor-lint-final-sources.log`. |
| Editor `npm run check:arch` | PASS | **PASS**, 0 нарушений | `editor-arch-after.log`, 633 модуля, 2079 зависимостей. |
| Editor `npm run test:coverage` | 420 / 0 / 0 | **420 / 0 / 0** | `editor-coverage-after.log`, 78 файлов. `trip-planner-draft.test.tsx` 14/14. |
| Editor coverage | 89,54% statements/lines, 74,16% branches, 95,38% functions | **89,54% / 74,16% / 95,38%** | Та же конфигурация Vitest, те же пороги. |
| Editor `npm run build` | PASS | **PASS** | `editor-build-after.log`, 27 страниц и SEO. |
| Editor `npm run e2e` | 104 / 21 / 15 | **126 / 7 / 15** в полном прогоне | `editor-e2e-final.log` и `.json`, Chrome, два workers. Тестирование фона разбито на 9 языков, итоговый набор вырос на 8. Среди исходных падений осталось 6 сценариев прежнего конструктора прогулок. Дополнительный отказ RSVP исправлен и проверен отдельно, 4 / 0 / 0. |
| Travel E2E с флагами | 6 / 0 / 0 | Сопоставимые **6 / 0 / 0**. Расширены проверки workspace и handoff. | `editor-travel-after.log`. Запуск с обычным Journey build дал 13/14. Путь к ассетам требует `--base=/private-journey/`. С таким build handoff прошёл отдельно на desktop и 390 px, по 1 / 0 / 0. |
| Journey `npm test` | 110 / 0 / 0 | **110 / 0 / 0** | `journey-unit-final.log`. |
| Journey `npm run build` | PASS | **PASS** | `journey-build-final.log`, штатное предупреждение о размере chunks. |
| Journey `npm run test:e2e` | 104 / 16 / 0 | **120 / 0 / 0** | `journey-e2e-final.log`, четыре профиля и два workers. До синхронизации stalled filter был 119 / 1 / 0. Целевой повтор GPS, фильтров и восстановления после синхронизации дал 24 / 0 / 0. |
| Journey Android `:app:testDebugUnitTest` | 14 / 0 / 0 | **14 / 0 / 0** | `journey-android.log`. Gradle offline, все 49 задач выполнены. |
| Backend `mvn -o -B verify` | 358 / 0 / 6 | **358 / 0 / 6** | `backend-verify.log`. Document 281 / 0 / 5, Routing 77 / 0 / 1. |
| Infra `unittest discover -s scripts/tests` | 16 / 0 / 0 | **16 / 0 / 0** | `infra-script-tests-after.log`. |
| Infra `unittest discover -s tests` | 19 / 2 / 0 | **22 / 0 / 0** | `infra-tests-before.log` на исходном коде Phase 1.1 18 / 3 / 0. После исправления 21 исходный тест и новая регрессия очистки дерева процессов. Пара исходных таймаутов также прошла в обратном порядке и параллельно. |
| Compose `config --quiet` | PASS | **PASS**, 8 конфигураций | `compose-after.log`, App, Gateway, Routing, Postgres, Walks, профили ostrovok-mcp и tools по отдельности и совместно. |

## Пропуски

В основном Editor Playwright исходные 15 пропусков обусловлены следующими условиями. Travel E2E запускается отдельно с включением соответствующих флагов.

| Существующий пропуск | Количество | Условие |
| --- | --- | --- |
| `live-place-photo.spec.ts:4` | 1 | Аудит реальных провайдеров при `LIVE_EXTERNAL=1`. |
| `trip-experience.spec.ts:25,101,154` | 5 | Флаг v1 проверяется в `beforeEach` для пяти тестов, два из которых дополнительно требуют v2. |
| `trip-list.spec.ts:4` | 1 | Флаг v1. |
| `trip-v2-workspace.spec.ts:12,51,95` | 5 | Флаг v2, включая три клавиатурных сценария по viewport. |
| `trip-walk-handoff.spec.ts:5,114,168` | 3 | Флаги v1/v2. Сквозной built handoff также требует собранный Journey. |

Backend сохранил шесть пропусков.

| Сценарий | Требование |
| --- | --- |
| `ImageRecognitionRegressionTest.replayRealScreenshotOcrWithoutRunningAProcessLocally` | Локальный каталог записанных OCR fixtures. |
| `ImageRecognitionRegressionTest.inspectSuppliedScreenshotWhenProvided` | Локальный путь к предоставленному скриншоту. |
| `NativeOcrTest.tesseractRecognizesSimpleText` | Установленный Tesseract. |
| `NativePdfLayoutTest.inspectLocalDocumentWhenExplicitlyProvided` | Путь к локальному PDF для диагностики. |
| `ServerOcrFixtureTest.serverResultsReconstructEditableTablesAndConsistentText` | Каталог результатов распознавания с сервера. |
| `PostgisPlacesTest` | Docker daemon и контейнер PostGIS. |

Compose config проверяет интерполяцию и структуру. Физический Android, Docker runtime, PostGIS, восстановление реальной резервной копии, SMTP и live-ответы внешних поставщиков в эти результаты не включены.

## Сопоставление 37 исходных записей

- **27** проходящих записей после исправления fixtures или runner. Из них 14 в Editor и 13 в Journey. Отдельно исправлена fixture RSVP, найденная только в повторном полном прогоне.
- **1** подтверждённый и исправленный дефект приложения. Ошибка повторного позиционирования Leaflet затрагивала **2** исходные записи.
- **2** исходные записи прошли повтор без установленной причины таймаута. Это холодная навигация в альбом Editor и ожидание записи GPS на desktop.
- **6** записей Editor остаются красными. Тесты используют старые элементы каталога и поиска, а требование о передаче фото и атрибуции нуждается в согласовании с действующим контрактом Journey.

Суммированы исходные записи, а не прогоны. Целевые повторы приведены отдельно. Условия 15 браузерных и 6 backend skip сверены с исходными.

## Воспроизведение

Исходные ревизии перечислены в [исходном baseline](architecture-evidence/BASELINE.md). Четыре worktree находятся в `C:/sandpr/.cache/quality-recovery-20261126`, журналы в его подкаталоге `logs`. Использованы Windows, Node 24.22.0, Python 3.15, Git Bash, Maven 3.9.9 с JDK 21 и target Java 17. Для Maven использован локальный кеш `-Dmaven.repo.local=C:/sandpr/.cache/maven`.

В Editor установленный Chrome задаётся `PLAYWRIGHT_CHANNEL=chrome`. Для Travel включаются `VITE_ENABLE_TRIP_PLANNER=true` и `VITE_ENABLE_TRIP_PLANNER_V2=true`. Для handoff переменная `TRIP_JOURNEY_DIST` указывает на результат `npm run build -- --base=/private-journey/` в Journey. При `TRIP_MOBILE_VIEWPORT=true` built integration использует 390×844.

Проверки запускались из worktree. Android sync выполнялся в отдельной копии. Системный Docker daemon оставался недоступен, для Compose использован пустой env-файл и синтетическая интерполяция. В продуктивные конфигурации и данные изменений не вносилось.

## Phase 1.1b — Journey handoff

Профили разделены по флагам и среде запуска. Журналы находятся в том же каталоге `logs`. Изменения контракта и проверяемые инварианты описаны в [ADR 0004](decisions/0004-private-journey-handoff.md) и [реестре E2E](E2E-FAILURE-REGISTER.md).

| Проверка | Результат и доказательство |
| --- | --- |
| Editor `check`, `lint:strict`, `check:arch` | 0 ошибок TypeScript, 0 предупреждений, 0 нарушений при 633 модулях и 2083 зависимостях. `handoff-check-retry.log`, `handoff-lint-retry.log`, `handoff-arch-retry.log`. |
| Editor coverage | 422 / 0 / 0 в 78 файлах. 89,54% statements/lines, 74,16% branches, 95,38% functions. Сопоставимое покрытие совпадает с Phase 1.1. `handoff-coverage-retry.log`. |
| Editor build | Сборка с Travel v1/v2 и SEO-проверка 27 страниц прошли. `handoff-build-retry.log`. |
| Journey unit, web, embed, Android JVM | Web unit 110 / 0 / 0, Android JVM 14 / 0 / 0. Две веб-сборки прошли, в том числе `--base=/private-journey/`. Android Gradle offline выполнил 49 задач с принудительным запуском unit tests. `handoff-journey-unit-gate.log`, `handoff-journey-build-gate.log`, `handoff-journey-build-embed.log`, `handoff-android.log`. |
| Journey E2E | 124 / 0 / 0 одним полным прогоном. Четыре viewport по 31 сценарию. Исторический поиск добавил по одной записи на viewport. `handoff-journey-isolated.log`. |
| Backend `mvn -o -B verify` | 360 / 0 / 6. Document 282 / 0 / 5, Routing 78 / 0 / 1. Существующие причины пропусков перечислены выше. Реальный API проверяется через Spring и H2. Отдельный PDFBox-тест проверил семь лицензированных фото, атрибуцию, нумерацию и перенос на страницы. `handoff-verify-final.log`, `handoff-seven-pdf-retry.log`. |
| Infra unittest и Compose config | `scripts/tests` 16 / 0 / 0, `tests` 22 / 0 / 0 в отдельных прогонах, включая штатные сценарии безопасного отказа импорта и сборки. Статическая проверка восьми конфигураций прошла. `handoff-infra-scripts-integrated.log`, `handoff-infra-closure.log`, `handoff-compose.log`. |
| Editor default | 132 / 0 / 15 одним полным прогоном с `VITE_FEATURE_EMAIL=false`. RSVP и шесть перенесённых записей прошли в этом прогоне. Существующие 14 пропусков Travel и один live-provider перечислены выше. Сценарий SMTP входит в отдельно запущенный email-enabled профиль. `handoff-default-retry.log`. |
| Editor email-enabled | 3 / 0 / 0. Почтовый retry, RSVP и публикация с открытием формы на телефоне. Почтовый профиль опирается на SMTP fixture. `handoff-email.log`. |
| Editor live-provider | 1 / 0 / 0. Chrome загрузил реальные метаданные Wikidata и Wikimedia Commons, разрешённое фото и подпись. Медиахранилище подменено локальной fixture. `handoff-live-actual.log`. |
| Editor, шесть перенесённых сценариев | 6 / 0 / 0 в отдельном детерминированном прогоне. Оба размера каталога вошли в результат. `handoff-six.log`. |
| Editor Travel v1/v2 | 14 / 0 / 0 одним полным прогоном. Включены список поездок, восстановление выбранного дня, клавиатурное управление на трёх ширинах, сохранение через API fixture и built Journey на стороне iframe. `handoff-travel-integrated.log`. |
| Built handoff, desktop и 390×844 | Два отдельных прохождения: по 1 / 0 / 0. Оба frontend-билда, закрытие Escape и возврат фокуса, настоящее сообщение из Journey, чтение и обновление поездки после сохранения через API fixture. `handoff-focus-desktop.log`, `handoff-focus-mobile.log`. |
| Повторы старых таймаутов | Editor 4 / 0 / 0: альбом и холодное открытие/переключение документов, по два раза. Journey desktop 4 / 0 / 0: GPS и stalled discovery, по два раза. `handoff-cold.log`, `handoff-journey-isolated-repeat.log`. |

Системный Docker daemon отсутствовал. PostGIS, миграция и восстановление реальной базы, работа на физических устройствах и реальная доставка SMTP остаются без результатов этой фазы. Проверка формирования PDF использовала локальное медиахранилище и тестовые записи.

Холодный повтор переключения документов занял 17,6 и 20,2 секунды целиком. Действие первоначальной навигации по trace заняло 2,33 и 2,76 секунды. Локальный лимит 45 секунд остаётся у теста с несколькими переходами. По этим двум замерам потребность в дополнительном запасе не подтверждается.

В Travel сценарий с сохранением в аккаунте и повторным входом в черновик дошёл до последнего выбора дня за 30 секунд. Для него установлен локальный предел 45 секунд, чтобы завершить третий переход и проверить восстановленный маркер. Лимит всего Playwright профиля сохранён.

Компактная панель проверена отдельно от широкого drag-and-drop. В default прошёл сценарий перетаскивания из dock и боковой панели на 1366 px. Отдельный повтор с параллельной нагрузкой дошёл до движения указателя и превысил 30 секунд. Широкий сценарий на 1600 px с выбранным набором данных прошёл в этом повторе. Целевой повтор двух сценариев дал 2 / 0 / 0. `handoff-default-retry.log`, `handoff-layout.log`, `handoff-layout-repeat.log`.

Первый полный Infra-прогон достиг дедлайнов в двух сценариях. Во втором прогоне наблюдались SIGPIPE тестового двойника и сбой очистки дерева процессов. Отдельный повтор на исходном двойнике воспроизвёл код 141 в миграции вместо заданного кода 37 на фазе импорта. После чтения stdin двойником и регистрации Windows launcher в Job Object оба исходных случая прошли в итоговом suite. `handoff-infra.log`, `handoff-infra-gate.log`, `handoff-infra-two.log`, `handoff-infra-fixture-gate.log`, `handoff-infra-closure.log`.

Интеграция Phase 1.1 выполнена в отдельных ветках `codex/quality-handoff-*`. Исходные SHA остаются предками интеграционных веток. Доработки Journey и Editor не пересекаются с исходными незакоммиченными файлами. В Backend работа касается ранее подготовленных архитектурных документов, которые перенесены в worktree без перезаписи оригиналов.
