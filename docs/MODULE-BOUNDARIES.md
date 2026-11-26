# Границы модулей CoDoc

## Владельцы решений и контрактов

| Модуль | Владеет | Публичная поверхность для соседей |
| --- | --- | --- |
| Editor `domain`, `application` | Команды редактирования, история взаимодействия, операции геометрии | Прикладные команды и порты. DTO документа на границе. |
| Editor `features/schemas`, `data-models`, `bindings` | Структура схемы и массива, значения, сопоставление с элементами | Идентификаторы, схемы и валидация. Store и UI создают текущую связанность. |
| Editor `features/templates`, `generation`, `library`, `workspace` | Сценарий открытия/сохранения, выпуски, версии и каталог | Вход React Router и DTO загрузки/сохранения. Сервис документов остаётся владельцем сохранённых моделей. |
| Editor `features/journeys` | Форма и локальный черновик поездки, составление публикаций и UI прогулок | HTTP, DTO `LocalTripDraft` и `SharedTrip`, iframe-мост. |
| Journey `domain` и `application` | Трек, геометрия, привязка точки, план прогулки, сценарии записи | Порты геокодирования, записи и репозиториев. Presentation вызывает use cases. |
| Journey `infrastructure` и Android | Browser storage, IndexedDB, Capacitor и foreground service | Порты приложения и JavaScript bridge. Native recorder владеет сырыми точками до завершения записи. |
| Document `template`, `schema`, `datamodel`, `bindings`, `workspace` | Шаблоны, версии, схемы, модели, рабочие документы | HTTP и отображаемые DTO. Обращения к репозиториям должны проходить через контракт владельца. |
| Document `generation`, `export`, `svg`, `pdf`, `recognition` | Подстановка, SVG/PDF, OCR, очереди рендера | `GenerationService`, `ExportFacade`, форматы и ограничения. |
| Document `journey`, `workflow`, `email`, `auth`, `clientapi` | Публикации, посещения, отправка и контроль доступа | Сервисы авторизации, короткие ссылки и ключи, публичные/закрытые контроллеры. |
| Routing `routing.domain`, `routing.application` | Расчёт дорожной геометрии и контракт провайдера | `CalculateRoad`, `RoadProvider`, публичный маршрутный HTTP. |
| Routing `routing.trip`, `walks` | Черновики поездок, POI, история зданий и мистические места | Внутренний HTTP поездок и открытая адресная/поисковая поверхность. |
| Infra | Конфигурация доставки, лимиты, данные runtime и маршрутизация HTTPS | Compose-профили, образы и проверяемые операции выпуска. |

## Фактические обходы

- Editor. `src/presentation/components/DataPanel.tsx` использует `schemaStore` и `modelStore` напрямую. `src/features/generation/ui/GenerationPage.tsx` читает внутренние хранилища соседних features. `src/features/library/DocumentLibraryPage.tsx` вызывает API публикаций из Journey. Различать штатную композицию UI и зависимость от чужого внутреннего состояния.
- Document. `journey/application/journey/PublicationsService.java:3–11` и `JourneyConstructorService.java:5–12` знают JPA-адаптеры. `JourneyPrintService.java:16` знает PDF-фабрику, а фабрика вызывает прикладные сервисы. `journey/domain/model/JourneyEntity.java:3` и `JourneyRoute.java:3` используют JPA по [предыдущему ADR](decisions/0002-journey-persistence-boundary.md).
- Routing. Граница дорожного ядра описана в `routing-service/src/test/java/ru/ticket/routing/RoutingArchitectureTest.java:9–18`. Инфраструктура прогулок имеет более широкие зависимости; `walks/application/map/AreaSearchCoordinator.java:6` импортирует HTTP-тип Spring. Правило прогулок проверяет отсутствие импорта web.
- HTTP между сервисами двунаправлен. Document передаёт операции черновика в Routing, а Routing пользуется Document для подтверждения владельца и печати. Разделение Maven-приложений исключает прямую зависимость между артефактами. Разделение ответственности при отказе остаётся задачей.

## Целевые правила для следующей фазы

1. Источник истины объявляется для каждой модели. Публичный DTO, модель домена и модель хранения разводятся там, где это устраняет обнаруженную связь.
2. Приложение обращается к инфраструктуре через порт. Spring wiring привязывает адаптер в composition root. Транзакция остаётся у сценария-владельца.
3. Импорты между features проходят через небольшой публичный вход, DTO или фасад. Исключения для миграции получают владельца и целевой срок. Типовые зависимости учитываются отдельно от runtime.
4. Репозиторий другого домена доступен через сервис/порт владельца. Существующие HTTP методы, идентификаторы, правила доступа и форматы выдачи проверяются контрактными тестами.
5. Новые диагностики работают в режиме отчёта до согласования baseline. Действующие правила проходят без изменений. Подробности в [DEPENDENCY-REPORT](DEPENDENCY-REPORT.md).
