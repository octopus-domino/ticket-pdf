# CoDoc Architecture Baseline 1.2

Срез объединяет результаты архитектурного аудита, восстановления проверок, Journey handoff и эксплуатационной приёмки. Изменения продукта ограничены завершёнными фазами. Phase 1.3 остаётся отдельной задачей. Статусы публикации, CI и тегов привязаны к полным SHA в [манифесте Infra](https://github.com/octopus-domino/ticket-infra/blob/codex/architecture-checkpoint-infra/releases/architecture-baseline-1.2.json).

## Что вошло

- Phase 1. Карта модулей, API, данных, зависимостей и результаты ARC-001–ARC-016. [Архитектура и доказательства](ARCHITECTURE-AS-IS.md), [ADR 0003](decisions/0003-architecture-hardening-in-place.md).
- Phase 1.1. TypeScript, strict lint, восстановление браузерных проверок и изоляция тестов оркестрации. [Quality Recovery](QUALITY-RECOVERY.md), [реестр E2E](E2E-FAILURE-REGISTER.md).
- Phase 1.1b. Передача снимка прогулки, хранение порядка, заметок и фото с атрибуцией, генерация PDF и совместимость старых прогулок. [ADR 0004](decisions/0004-private-journey-handoff.md), [API-контракты](API-CONTRACTS.md).
- Phase 1.2. Внутренние liveness и readiness с проверкой схем, независимый запуск сервисов, измеренный бюджет Tyk и синтетическое восстановление обеих схем. [Production Safety](PRODUCTION-SAFETY.md), [Failure Mode Analysis](FAILURE-MODE-ANALYSIS.md), [Backup and restore](BACKUP-RESTORE-DRILL.md), [измерения Tyk](RESOURCE-MEASUREMENTS.md).

## История интеграции

Ревизии до интеграции сохранены в каждом клоне как `refs/backup/architecture-baseline-1.2/{local-default,remote-default,quality-handoff,production-safety}`. Backend, Infra и Journey допускают fast-forward. В Editor replay фазовых коммитов сохраняет историю `master`, включая существующий Pages fix и реализацию планировщика. Проверка ролей Testing Library из старой ветки относилась к отсутствующим в `master` вызовам. ESLint-исправления перенесены. В альбоме выбран локальный шрифт fixture. В handoff-тестах сохранены исходные проверки CityWalls, использован фактический ключ черновика `codoc/trip-planner/draft/v2` и перенесены проверки прогулок на действующий контракт iframe.

Backend-срез `6a88fe8c5511e240b36e17ba5ca06c1bfb6477c4` отличается от серверных JAR `58b597b599f543125d5a796d8d5928c814eb4201` только документацией. Серверный restore выполнен на Infra `dbee8d593ae1eae85d98fc7dd449832d002922cc`. Последующие Infra-коммиты добавляют preflight лабораторного restore и локальные сценарии отказов. Они имеют отдельные доказательства. Результаты прежних прогонов и точные ограничения собраны в [BASELINE-AFTER.md](BASELINE-AFTER.md).

| Компонент | Ревизия исходников интеграционной проверки |
| --- | --- |
| Editor | `3326d67ec43ed8e598e1a6895185c08a95faa328` |
| Backend | `6a88fe8c5511e240b36e17ba5ca06c1bfb6477c4` |
| Infra | `4d2b627327bd2741530d20e67a013a8e66f09e7e` |
| Journey | `39c3825957b9e38df93acc05c87cac7a4c3ccd9a` |

На объединённом Editor прошли TypeScript, strict lint, проверка архитектуры и production build. Unit-прогон дал 428/428. Покрытие составило 88,79% строк и 74,12% ветвлений против 89,54% и 74,16% в отдельной ветке Phase 1.2. Граф исходников различается из-за сохранения изменений `master`. Пороги и тестовые исключения прежние.

Backend `mvn -o -B verify` локально дал 362 успешных теста и шесть исходных пропусков, включая контроль архитектуры и проверку печати с фото. В исходном GitHub Quality на шаге `mvn -B verify` был exit code 1. [Запуск GitHub Quality](https://github.com/octopus-domino/ticket-pdf/actions/runs/45905818574)

Обе сборки Journey, 110 unit-тестов и 14 Android JVM-тестов прошли. В Infra прошли suite скриптов 19/19, восемь конфигураций Compose и проверка лимитов Tyk, включая отклонение пары 300/160 МиБ. Полные результаты браузерных профилей и повторных прогонов фиксируются отдельно в манифесте с указанием среды.

## Эксплуатационные ограничения

ARC-013 подтверждён для проверенных H2 и PostgreSQL-профилей. ARC-004 подтверждён в испытанных комбинациях профиля и флага. ARC-014 подтверждён измерениями для краткой нагрузки и отказа Redis. Синтетическая часть ARC-016 прошла, полнота штатного резервного копирования остаётся открытой. Все открытые ARC и дальнейшие задачи ведутся в [ARCHITECTURE-BACKLOG.md](ARCHITECTURE-BACKLOG.md#пауза-после-baseline-12).

Приёмка runtime частичная. Повтор Linux-сценариев SMTP, OCR, BRouter и прерывания генерации на сервере отложен после SSH timeout. Локальные результаты относятся к Windows/H2 и synthetic doubles. Вне проверенных комбинаций остаются PostgreSQL с выключенным Travel, часть отрицательных сценариев restore, физические мобильные устройства, реальная доставка SMTP и длительная нагрузка. Замеры Tyk и RTO относятся к одноразовому окружению. Production release на `codoc.site` в рамках фиксации не выполнялся.

## Восстановление набора исходников

Согласованный набор фиксируется в `ticket-infra/releases/architecture-baseline-1.2.json`. Для официально опубликованной точки в свежем каталоге:

```bash
for repo in ticket-editor ticket-pdf ticket-infra private-jorney; do
  directory="$repo"
  if [ "$repo" = private-jorney ]; then directory=private_jorney; fi
  git clone "https://github.com/octopus-domino/$repo.git" "$directory"
  git -C "$directory" fetch origin tag architecture-baseline-1.2
  git -C "$directory" switch --detach architecture-baseline-1.2
  git -C "$directory" rev-parse HEAD
done
```

До публикации тега используйте полные SHA кандидатов манифеста. Зависимый embedded Journey собирается из зафиксированной ревизии с `npm run build -- --base=/private-journey/`. Infra build ожидает соседние каталоги и имя `private_jorney` для исходников Journey. Команды воспроизведения профилей приведены в [baseline](BASELINE-AFTER.md) и [Production Safety](PRODUCTION-SAFETY.md). Манифест отдельно фиксирует implementation SHA Infra. Коммит манифеста содержит только release metadata.

Получить манифест кандидата и посмотреть ревизию конкретного репозитория можно через его опубликованную интеграционную ветку. Например:

```bash
git -C ticket-infra fetch origin codex/architecture-checkpoint-infra
git -C ticket-infra show FETCH_HEAD:releases/architecture-baseline-1.2.json
git -C ticket-editor fetch origin codex/architecture-checkpoint-editor
git -C ticket-editor switch --detach <candidateSha-из-манифеста>
```
