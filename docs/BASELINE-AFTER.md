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
