# Аудит текущего состояния Journey

## Границы, хранение и PDF

- Маршруты обслуживает цепочка `RouteAddressController → JourneyRouteService → RoadRouter/JourneyRouteRepository`. Адаптеры роутеров объединяет `FallbackRoadRouter`.
- Поиск мест проходит через `PlaceRecommendationsController`, `OsmPlaceService` и классы `walk/`. Оркестратор области объединяет источники, ограничивает параллелизм и управляет кэшем. Дома обслуживает `BuildingHistoryController`.
- `JourneyController` и `PublicationsController` используют `JourneyService` для выдачи, публикации и управления. JPA хранит публикации в `public_journeys` с JSON содержимого, а снимки маршрутов в `journey_routes`. Вложенная прогулка привязана через `walkId`.
- `JourneyPdfController` получает снимок публикации, `JourneyDocumentFactory` формирует документ. Общие макеты и шрифты связывают предпросмотр и PDF. API, идентификаторы, ключи управления и доступ владельца входят в регресс.
- `poetry/` обслуживает открытки. Эти сценарии используют общие контракты публикации.

## Карты

- `RouteEditor` в CoDoc использует `RouteMap` на MapLibre GL. Карта передаёт координаты клика через `onPick`, отображает остановки и линии и формирует снимок для экспорта. Есть альтернативная карта Яндекса через iframe. Перестановку в списке обслуживает `RouteReorderHandle`. Лассо, рисование линий и перетаскивание маркеров потребуют новых инструментов.
- `MapView` в Journey использует Leaflet и MarkerCluster. Она обрабатывает нажатия по карте, объектам и кластерам. `SharedWalk` передаёт точки и геометрию через iframe. Импорт должен сохранять исходный снимок.
- Планировщик подключается в конструкторе поездок CoDoc. Форма остаётся доступна для точных правок.

## Базовые проверки

- CoDoc Vitest: 384 теста прошли.
- Journey: 101 тест прошёл.
- Backend Maven: 314 тестов, 0 ошибок, 6 пропущено. Использован локальный Maven-кэш с переопределением версии PostgreSQL JDBC на 42.2.15. Целевая Java 17, запуск на JDK 21.

## Карта ссылок между классами

Инвентарь построен по ссылкам на имена классов. Связывание Spring во время выполнения в таблицу не входит.

| Класс | Область | Ссылки в Journey |
|---|---|---|
| `AddressSearchService.java` | Publications and shared services | - |
| `BRouterHttpRoadRouter.java` | Routing and POI | RoadRouter |
| `BRouterRoadRouter.java` | Routing and POI | RoadRouter |
| `BuildingHistory.java` | Routing and POI | - |
| `BuildingHistoryController.java` | Routing and POI | BuildingHistoryService |
| `BuildingHistoryRepository.java` | Routing and POI | BuildingHistory |
| `BuildingHistoryService.java` | Routing and POI | BuildingHistory, BuildingHistoryRepository, BuildingHistoryText, WalkThemeService |
| `BuildingHistoryText.java` | Routing and POI | BuildingHistoryService |
| `EventDocumentLayout.java` | Publications and shared services | JourneyService, JourneySiteService |
| `FallbackRoadRouter.java` | Routing and POI | BRouterHttpRoadRouter, BRouterRoadRouter, OsrmFossgisRoadRouter, RoadRouter, ValhallaRoadRouter |
| `HoithBootstrap.java` | Routing and POI | BuildingHistoryRepository, HoithImporter |
| `HoithImporter.java` | Routing and POI | BuildingHistory, BuildingHistoryService |
| `JourneyAlbum.java` | Publications and shared services | - |
| `JourneyAlbumController.java` | Publications and shared services | JourneyAlbumService |
| `JourneyAlbumRepository.java` | Publications and shared services | JourneyAlbum |
| `JourneyAlbumService.java` | Publications and shared services | JourneyAlbum, JourneyAlbumRepository, JourneyAlbumTemplate, JourneyMedia, JourneyMediaService, JourneyService, PublicationAccess |
| `JourneyAlbumTemplate.java` | Publications and shared services | JourneyConstructorSource, JourneyService |
| `JourneyArtworkController.java` | Publications and shared services | JourneyDocumentFactory, JourneyService, JourneySiteService |
| `JourneyAttachments.java` | Publications and shared services | JourneyContentPolicy, JourneyService |
| `JourneyBackgrounds.java` | Publications and shared services | - |
| `JourneyBatch.java` | Publications and shared services | - |
| `JourneyBatchRepository.java` | Publications and shared services | JourneyBatch |
| `JourneyBranding.java` | Publications and shared services | - |
| `JourneyBrandingLayout.java` | Publications and shared services | JourneyBranding, JourneyMedia, JourneyMediaService |
| `JourneyConstructorController.java` | Publications and shared services | JourneyConstructorCopyService, JourneyPrintSourceService, JourneyService |
| `JourneyConstructorCopyService.java` | Publications and shared services | JourneyDocumentFactory, JourneyService |
| `JourneyConstructorSource.java` | Publications and shared services | - |
| `JourneyContentPolicy.java` | Publications and shared services | JourneyEntity, JourneyMoney, JourneyService |
| `JourneyController.java` | Publications and shared services | JourneyAttachments, JourneyContentPolicy, JourneyService |
| `JourneyDocumentFactory.java` | Publications and shared services | EventDocumentLayout, JourneyAlbumService, JourneyAlbumTemplate, JourneyBranding, JourneyBrandingLayout, JourneyConstructorSource, JourneyContentPolicy, JourneyFieldLabels, JourneyGridLayout, JourneyMediaService, JourneyPoetryTemplate, JourneyPrintCopy, JourneyPrintSourceRepository, JourneyRouteLayout, JourneyService, JourneySiteService, PoetryCardsService, PoetryDisplayText, ProposalDocumentLayout |
| `JourneyEntity.java` | Publications and shared services | - |
| `JourneyFieldLabels.java` | Publications and shared services | - |
| `JourneyGridLayout.java` | Publications and shared services | JourneyFieldLabels, JourneyMoney, JourneyService, JourneyTypography |
| `JourneyMedia.java` | Publications and shared services | - |
| `JourneyMediaController.java` | Publications and shared services | JourneyMediaService |
| `JourneyMediaRepository.java` | Publications and shared services | JourneyMedia |
| `JourneyMediaService.java` | Publications and shared services | JourneyMedia, JourneyMediaRepository |
| `JourneyMoney.java` | Publications and shared services | - |
| `JourneyPdfController.java` | Publications and shared services | JourneyDocumentFactory, JourneyService |
| `JourneyPoetryTemplate.java` | Publications and shared services | JourneyAlbumTemplate, JourneyConstructorSource, JourneyService, PoetryCardsService, PoetryDisplayText |
| `JourneyPrintCopy.java` | Publications and shared services | JourneyService |
| `JourneyPrintSource.java` | Publications and shared services | - |
| `JourneyPrintSourceRepository.java` | Publications and shared services | JourneyPrintSource |
| `JourneyPrintSourceService.java` | Publications and shared services | JourneyAlbumService, JourneyConstructorSource, JourneyDocumentFactory, JourneyPrintSource, JourneyPrintSourceRepository, JourneyService, PublicationAccess |
| `JourneyRepository.java` | Publications and shared services | JourneyEntity |
| `JourneyResponse.java` | Publications and shared services | - |
| `JourneyResponseRepository.java` | Publications and shared services | JourneyResponse |
| `JourneyReview.java` | Publications and shared services | - |
| `JourneyReviewController.java` | Publications and shared services | JourneyReviewService |
| `JourneyReviewRepository.java` | Publications and shared services | JourneyReview |
| `JourneyReviewService.java` | Publications and shared services | JourneyRepository, JourneyReview, JourneyReviewRepository, JourneyService, PublicationAccess |
| `JourneyRoute.java` | Routing and POI | - |
| `JourneyRouteLayout.java` | Routing and POI | JourneyFieldLabels, JourneyMediaService, JourneyRouteService, JourneyService, JourneyTypography, RoutePointLabels, WalkLabels |
| `JourneyRouteRepository.java` | Routing and POI | JourneyRoute |
| `JourneyRouteService.java` | Routing and POI | BRouterRoadRouter, JourneyMediaService, JourneyRoute, JourneyRouteRepository, RoadRouter |
| `JourneyService.java` | Publications and shared services | JourneyBatch, JourneyBatchRepository, JourneyBranding, JourneyContentPolicy, JourneyEntity, JourneyFieldLabels, JourneyRepository, JourneyResponse, JourneyResponseRepository, JourneySiteService, PoetryCardsService, ProposalContent, PublicationAccess, ShortCodeGenerator |
| `JourneySite.java` | Publications and shared services | - |
| `JourneySiteController.java` | Publications and shared services | JourneySiteService |
| `JourneySiteRepository.java` | Publications and shared services | JourneySite |
| `JourneySiteService.java` | Publications and shared services | JourneyBackgrounds, JourneyBranding, JourneyService, JourneySite, JourneySiteRepository, PublicationAccess |
| `JourneyTypography.java` | Publications and shared services | - |
| `MapProvidersController.java` | Publications and shared services | - |
| `MysticPlace.java` | Routing and POI | - |
| `MysticPlaceBootstrap.java` | Routing and POI | MysticPlaceImporter, MysticPlaceRepository |
| `MysticPlaceImporter.java` | Routing and POI | MysticPlace, MysticPlaceRepository |
| `MysticPlaceRepository.java` | Routing and POI | MysticPlace |
| `MysticThemeSource.java` | Routing and POI | BRouterRoadRouter, MysticPlace, MysticPlaceRepository, RoadRouter, WalkThemeService |
| `OpeningHoursEvaluator.java` | Publications and shared services | OsmOpeningHours |
| `OsmOpeningHours.java` | Publications and shared services | - |
| `OsmPlaceService.java` | Routing and POI | BRouterRoadRouter, OsmOpeningHours, OverpassQueryClient, PlaceRecommendationsController, RoadRouter, WalkRemote, WalkThemeService |
| `OsrmFossgisRoadRouter.java` | Routing and POI | RoadRouter |
| `PlaceRecommendationsController.java` | Routing and POI | BRouterRoadRouter, JourneyRouteService, OsmOpeningHours, OsmPlaceService, PhotoEnricher, RoadRouter, WalkAreaSearchService, WalkThemeProperties, WalkThemeService, WikimapiaPlaces |
| `poetry/PoemShipMcpClient.java` | Poetry | PoetryFetchBudget |
| `poetry/PoemShipProvider.java` | Poetry | PoeTreeProvider, PoemShipMcpClient, PoemShipTextResolver, PoetryAudienceEvidence, PoetryDbProvider, PoetryProvider, PoetryQuery, PoetryWork, WikisourcePoems |
| `poetry/PoemShipTextResolver.java` | Poetry | PoetryAudienceEvidence, PoetryDbProvider, PoetryFetchBudget, PoetryQuery, PoetryWork |
| `poetry/PoetDiscovery.java` | Poetry | PoetryRemoteJson |
| `poetry/PoeTreeProvider.java` | Poetry | PoetryDbProvider, PoetryProvider, PoetryQuery, PoetryRemoteJson, PoetryWork |
| `poetry/PoetryAudienceEvidence.java` | Poetry | - |
| `poetry/PoetryCardsService.java` | Poetry | PoetryQuery, PoetrySelection, PoetrySet, PoetrySetRepository, PoetrySourceChain, PoetryWeatherService, PoetryWork, WikisourcePoems |
| `poetry/PoetryController.java` | Poetry | PoetryCardsService, PoetryWeatherService |
| `poetry/PoetryDbProvider.java` | Poetry | PoetDiscovery, PoetryProvider, PoetryQuery, PoetryRemoteJson, PoetryWork |
| `poetry/PoetryDisplayText.java` | Poetry | PoetryCardsService |
| `poetry/PoetryFetchBudget.java` | Poetry | - |
| `poetry/PoetryProvider.java` | Poetry | PoetryQuery, PoetryWork |
| `poetry/PoetryQuery.java` | Poetry | PoetryWeatherService |
| `poetry/PoetryRemoteJson.java` | Poetry | PoetryFetchBudget |
| `poetry/PoetrySelection.java` | Poetry | PoetryAudienceEvidence, PoetryWeatherService, WikisourcePoems |
| `poetry/PoetrySet.java` | Poetry | - |
| `poetry/PoetrySetRepository.java` | Poetry | PoetrySet |
| `poetry/PoetrySourceChain.java` | Poetry | PoeTreeProvider, PoemShipProvider, PoetryDbProvider, PoetryFetchBudget, PoetryProvider, PoetryQuery, PoetrySelection, PoetryWork, WikisourcePoems |
| `poetry/PoetryWeatherService.java` | Poetry | PoetryRemoteJson |
| `poetry/PoetryWork.java` | Poetry | PoetryQuery, PoetrySelection, WikisourcePoems |
| `poetry/WikisourcePoems.java` | Poetry | PoetDiscovery, PoetryDbProvider, PoetryRemoteJson, PoetrySelection, PoetryWork |
| `ProposalContent.java` | Publications and shared services | - |
| `ProposalDocumentLayout.java` | Publications and shared services | JourneyService, ProposalContent |
| `PublicationAccess.java` | Publications and shared services | JourneyEntity, JourneyRepository, JourneySite, JourneySiteRepository |
| `PublicationsController.java` | Publications and shared services | JourneyContentPolicy, JourneyService, JourneySiteService, PublicationsService |
| `PublicationsService.java` | Publications and shared services | JourneyBatchRepository, JourneyContentPolicy, JourneyRepository, JourneyService, JourneySiteRepository, JourneySiteService, PublicationAccess |
| `RoadRouter.java` | Routing and POI | - |
| `RouteAddressController.java` | Routing and POI | AddressSearchService, JourneyRouteService, RoadRouter |
| `RoutePointLabels.java` | Routing and POI | - |
| `ShortCodeGenerator.java` | Publications and shared services | - |
| `ShortLinkController.java` | Publications and shared services | JourneyDocumentFactory, JourneyService |
| `ValhallaRoadRouter.java` | Routing and POI | RoadRouter |
| `walk/AreaResultCache.java` | Walk | PlaceRecommendationsController, WalkAreaSearchService, WikimapiaPoiSourceImpl |
| `walk/AreaSearchCoordinator.java` | Walk | - |
| `walk/AreaThemeSelection.java` | Walk | OsmPoiSource, WalkThemeProperties, WikimapiaPoiSourceImpl |
| `walk/CommonsImages.java` | Walk | - |
| `walk/OsmPoiSource.java` | Walk | OsmPlaceService, WalkCache, WalkThemeProperties |
| `walk/OverpassQueryClient.java` | Walk | WalkRemote |
| `walk/PhotoEnricher.java` | Walk | CommonsImages, JourneyMediaService, JourneyRouteService, WalkBudget, WalkCache, WalkRemote, WikimapiaPoiSource |
| `walk/PhotonAreaPoiSource.java` | Walk | PhotonPoiSource, WalkRemote, WalkThemeProperties, WikimapiaPoiSourceImpl |
| `walk/PhotonPoiSource.java` | Walk | WalkCache, WalkRemote, WalkThemeProperties |
| `walk/WalkAreaSearchService.java` | Walk | AreaResultCache, AreaSearchCoordinator, AreaThemeSelection, MysticThemeSource, OpeningHoursEvaluator, OsmPoiSource, PhotonAreaPoiSource, PlaceRecommendationsController, WalkBudget, WalkThemeProperties, WikimapiaPoiSource, WikimapiaPoiSourceImpl |
| `walk/WalkBudget.java` | Walk | - |
| `walk/WalkCache.java` | Walk | - |
| `walk/WalkLabels.java` | Walk | - |
| `walk/WalkRemote.java` | Walk | WalkBudget |
| `walk/WalkThemeProperties.java` | Walk | - |
| `walk/WalkThemeService.java` | Walk | BuildingHistoryService, MysticThemeSource, OpeningHoursEvaluator, OsmPoiSource, PhotonPoiSource, WalkBudget, WalkCache, WalkThemeProperties, WikidataPopularity, WikimapiaPoiSource, WikimapiaPoiSourceImpl |
| `walk/WikidataPopularity.java` | Walk | WalkCache, WalkRemote |
| `walk/WikimapiaPoiSource.java` | Walk | WalkThemeProperties |
| `walk/WikimapiaPoiSourceImpl.java` | Walk | OsmPlaceService, WalkCache, WalkRemote, WalkThemeProperties, WikimapiaKeyProvider, WikimapiaPoiSource, WikimapiaRateLimiter |
| `WikimapiaKeyProvider.java` | Publications and shared services | - |
| `WikimapiaPlaces.java` | Routing and POI | PlaceRecommendationsController, WikimapiaRateLimiter |
| `WikimapiaRateLimiter.java` | Publications and shared services | - |

## HTTP-контракты

Существующие пути по аннотациям контроллеров.

- `BuildingHistoryController`
  - `GET /api/journeys/buildings`
  - `GET /api/journeys/buildings/nearby`
- `JourneyAlbumController`
  - `GET /api/journeys/{id}/album`
  - `POST /api/journeys/{id}/album/invitation`
  - `POST /api/journeys/{id}/album/photos`
  - `PUT /api/journeys/{id}/album/photos/{photoId}`
  - `DELETE /api/journeys/{id}/album/photos/{photoId}`
  - `GET /api/journeys/{id}/album/photos/{photoId}`
  - `PUT /api/journeys/{id}/album/order`
- `JourneyArtworkController`
  - `POST /api/journeys/artwork`
  - `GET /api/journeys/fonts/{face}`
- `JourneyConstructorController`
  - `POST /api/publications/{id}/constructor`
  - `PUT /api/publications/{id}/constructor`
  - `PUT /api/publications/sites/{slug}/constructor`
  - `POST /api/publications/{id}/constructor/publications`
  - `PUT /api/publications/batches/{id}/constructor`
- `JourneyController`
  - `POST /api/journeys`
  - `GET /api/journeys/{id}`
  - `GET /api/journeys/{id}/manage`
  - `PUT /api/journeys/{id}`
  - `DELETE /api/journeys/{id}`
  - `POST /api/journeys/{id}/responses`
  - `GET /api/journeys/{id}/responses`
  - `GET /api/journeys/{id}/status`
  - `POST /api/journeys/{id}/consume`
  - `GET /api/journeys/batches/{id}`
  - `POST /api/journeys/{id}/status`
  - `GET /api/journeys/{id}/calendar`
  - `GET /api/journeys/{id}/contact`
- `JourneyMediaController`
  - `POST /api/journeys/media`
  - `GET /api/journeys/media/{id}`
  - `POST /api/journeys/media/background`
- `JourneyPdfController`
  - `GET /api/journeys/batches/{id}/pdf`
  - `GET /api/publications/batches/{id}/pdf`
  - `GET /api/journeys/batches/{id}/zip`
  - `GET /api/publications/batches/{id}/zip`
  - `GET /api/journeys/{id}/pdf`
- `JourneyReviewController`
  - `POST /api/journeys/{id}/reviews`
  - `GET /api/journeys/{id}/reviews`
- `JourneySiteController`
  - `GET /api/journey-sites/{slug}`
  - `GET /api/journey-sites/availability`
  - `POST /api/journey-sites`
  - `PUT /api/journey-sites/{slug}`
- `MapProvidersController`
  - `GET /api/journeys/maps/providers`
- `PlaceRecommendationsController`
  - `GET /api/journeys/routes/places/wikimapia-{id}`
  - `GET /api/journeys/routes/places/themed`
  - `GET /api/journeys/routes/places/themes`
  - `GET /api/journeys/routes/places/themed`
  - `POST /api/journeys/routes/places/themed/enrich`
  - `GET /api/journeys/routes/places`
- `PoetryController`
  - `GET /api/journeys/poetry/weather`
  - `POST /api/journeys/poetry`
  - `GET /api/journeys/poetry/{id}`
- `PublicationsController`
  - `GET /api/publications`
  - `GET /api/publications/sites`
  - `GET /api/publications/batches`
  - `GET /api/publications/batches/{id}`
  - `POST /api/publications/{id}/status`
  - `POST /api/publications/claim`
  - `PUT /api/publications/sites/{slug}`
  - `GET /api/publications/{id}`
  - `PUT /api/publications/{id}`
  - `DELETE /api/publications/{id}`
  - `GET /api/publications/{id}/responses`
  - `POST /api/publications/{id}/consume`
- `RouteAddressController`
  - `GET /api/journeys/routes/addresses`
  - `POST /api/journeys/routes`
  - `GET /api/journeys/routes/{id}`
  - `POST /api/journeys/routes/road`
- `ShortLinkController`
  - `GET /api/links/{code}`
