# Исходный срез Phase 1 и результаты проверок

Состояние Git записано до создания документов аудита. Версии относятся к локальным HEAD. Сравнение с origin отражает локальные tracking refs на момент среза.

| Репозиторий | Корень | Ветка | Полный commit SHA | Исходное состояние |
| --- | --- | --- | --- | --- |
| ticket-editor | `C:/sandpr/ticket-editor` | `codex/travel-experience-v3` | `d2d26de63705c0f337226c5940dec600241b81c8` | 1 изменённый файл `Dump-Folder-front.ps1`. Staging пуст. |
| ticket-pdf | `C:/sandpr/ticket-pdf` | `main` | `e237ae125d89d1434b3d7729c69243be546673e3` | Чистый индекс и рабочий каталог до аудита. |
| ticket-infra | `C:/sandpr/ticket-infra` | `main` | `3ade22148b8efb4f535e0f7565c515fdec8d88db` | 1 изменённый отчёт и 5 untracked отчётов в `reports/`. Staging пуст. |
| private-jorney | `C:/sandpr/private_jorney` | `main` | `d8ca76a1b3778846d937895775377a03d90dcb87` | 16 изменённых файлов Android/TS, 17 untracked записей при normal-виде Git, включая каталоги. Staging пуст. |

Изолированные копии сделаны через `git archive <SHA>`. Графы и тесты считают ревизии из таблицы. Использованы существующие локальные зависимости Node, отдельные test/build outputs, Windows, Node 24.22.0, Python 3.15, Maven 3.9.9 с JDK 21 и целевой Java 17. Для browser-проверок используются localhost и тестовые fixtures. Логи вне Git в `C:/sandpr/.cache/architecture-phase1-20261125/logs/`.

## Штатные результаты

| Репозиторий и команда | Результат | Доказательство / оговорка |
| --- | --- | --- |
| Editor `npm run check` | **Fail** | Product typecheck проходит. Цепочка останавливается на `typecheck:test`: 5 TS2769 в `tests/trip-planner-draft.test.tsx:58,68,69,79,85`, `exact` не входит в тип ByRoleOptions. |
| Editor `npm run lint:strict` | **Fail** | 3 имеющихся предупреждения `react-hooks/exhaustive-deps` в `src/presentation/components/RichTableCellEditor.tsx:49` и `TableCellEditor.tsx:25`. Лимит CI остаётся 0. |
| Editor `npm run check:arch` | **Pass** | Штатная `.dependency-cruiser.cjs`. Расширенная инвентаризация в [DEPENDENCY-REPORT](../DEPENDENCY-REPORT.md). |
| Editor `npm run typecheck:e2e`, `npm run check:locales` | **Pass отдельно** | Запущены отдельно после остановки общего check. В каталоге 2451 UI-сообщение и 0 пропусков переводов. |
| Editor `npm run test:coverage` | **Pass** | 78 файлов, 420 тестов. По измеряемому набору Vitest — statements/lines 89.54%, branches 74.16%, functions 95.38%. Область охвата определена в `vitest.config.ts`. |
| Editor `npm run build` | **Pass** | Typecheck приложения, Vite, пререндер 27 публичных страниц, SEO. |
| Editor `npm run e2e` | **Fail** | 104 прошли, 21 упал, 15 skip из 140. Локальный Chrome и исходные флаги. Падения охватывают импорт и открытие документов, вёрстку, прогулки, письмо и альбомы. |
| Editor Playwright, флаги поездок включены | **Pass** | 6/6 в `tests/e2e/trip-experience.spec.ts` и `trip-list.spec.ts`. Отдельный запуск на той же ревизии. |
| Journey `npm test`, `npm run build` | **Pass** | 110/110, tsc и Vite. Есть предупреждение размера chunks. Node-тесты не генерируют процент покрытия. |
| Journey Playwright `npm run test:e2e -- --workers=1` | **Fail** | 104 прошли, 16 упали из 120 на четырёх viewport. Ошибки запуска/восстановления карточек Москвы и Петербурга, GPS-отчёта, фильтра и восстановления маршрута. `tests/recording.ui.ts:48,238,257`, `tests/ui.spec.ts:76,140,367`. Причины всех 16 требуют раздельного воспроизведения. |
| Journey Android `testDebugUnitTest` | **Pass после подготовки** | Архивная копия сначала не содержала сгенерированный `cordova.variables.gradle`. После `cap sync android` Gradle offline прошёл. Повтор с `--rerun-tasks` также прошёл, 14 тестов. Физическое устройство и Xcode в локальной проверке не использовались. |
| Java `mvn -o -B verify` | **Pass** | Document 286, Routing 78, суммарно 364 тестовых записи, 0 ошибок, 6 skip. Настоящий PostGIS и нативные условия проверяются отдельно. Выполнены существующие ArchUnit тесты и упаковка модулей. |
| Infra `python -m unittest discover -s scripts/tests` | **Pass** | 16 тестов инструментов с локальными doubles. |
| Infra `python -m unittest discover -s tests` | **Проходы зависят от условий запуска** | 19 из 21 прошли в общем прогоне. `test_import_failure_keeps_application_offline_instead_of_resuming_h2_writes` и `test_legacy_build_failure_restores_old_app` прерваны по TimeoutExpired 120 s. Отдельный повтор этих двух тестов прошёл за 33 s. Причину различия и стабильность CI ещё требуется проверить. |
| Docker Compose и восстановление | **Config проходит, runtime не проверен** | Локальный Compose 2.5.1 валидирует отдельные Routing и Gateway, основной App и совмещённый Postgres/Walks с профилями. Использован пустой локальный env-файл с тестовыми параметрами вместо недоступного в Windows `/dev/null`. Docker daemon недоступен. Реальная БД, конкуренция и restore требуют изолированного Docker-окружения. |

В браузерном логе Editor присутствуют таймауты, ошибки ожиданий видимости и ошибки локального Vite proxy. Падение теста доказывает расхождение в этом окружении. Причина может находиться в приложении, fixture, интеграции или настройке runner. [Неуспешные сценарии обоих прогонов](browser-baseline-failures.csv) приведены без payload, снимков и данных аккаунтов.

`mvn dependency:tree` в offline-режиме остановился на отсутствии `maven-dependency-plugin:3.7.0` в кэше. Граф приложений подтверждён POM и независимой упаковкой обоих модулей. Состав `BOOT-INF/lib` снят с JAR успешного `verify`.

## Ограничения статической инвентаризации

- Индексы импорта покрывают статические, распознаваемые динамические и type-only TS связи. Java снапшоты исходников и байткода учитывают разные виды ссылок. Reflection, DI и внешние сервисы проверяются по конфигурации.
- REST список строится по декларациям Spring MVC. Условные варианты, security callbacks, actuator и прокси учтены в [API-CONTRACTS](../API-CONTRACTS.md). Связь со сторонним клиентом по исходникам этого workspace подтвердить невозможно.
- Сопоставление SQL и JPA описывает модель и переносы по исходникам. Аудит не меняет схему, пользователей или production и не запускает публикацию.
