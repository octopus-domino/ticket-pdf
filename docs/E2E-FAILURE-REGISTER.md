# Реестр браузерных отказов Phase 1

Имена и позиции относятся к исходным SHA из [Phase 1 BASELINE](architecture-evidence/BASELINE.md). Подробности связанных тестов, фактический результат и минимальные исправления привязаны к группе причины. Исходные traces содержат действия и snapshot DOM. Для импорта и восстановления маршрута дополнительно просмотрены кадры screencast. Сетевые доказательства приведены по пути запроса и статусу.

**Классы.** A — подтверждённый дефект приложения. B — устаревший тест или fixture. C — конфигурация runner. D — отказ или нестабильность внешней зависимости. E — недетерминированность. F — причина пока не установлена.

**Флаги Phase 1.** У Editor флаги поездок v1/v2 и LIVE_EXTERNAL не заданы. Email в baseline не включён. В повторном основном запуске включён только VITE_FEATURE_EMAIL. В Journey флаги поездок не применимы. В baseline не задан VITE_MOS_DATA_API_KEY. В новом runner используется синтетическое значение с перехватом HTTP. Chrome для Editor запущен через PLAYWRIGHT_CHANNEL. Четыре проекта Journey используют штатный Playwright Chromium. Форм-фактор указан по trace, включая переопределения.

| № | Исходный сценарий | Файл и строка | Браузер и viewport | Причина |
| --- | --- | --- | --- | --- |
| 1 | phone creates an album and immediately adds a caption and photograph | `tests/e2e/album-create.spec.ts:3:1` | Chrome 390×844 | E1 |
| 2 | selected lossless artwork survives the native document export at the original resolution | `tests/e2e/background-library.spec.ts:67:1` | Chrome 1280×720 | E2 |
| 3 | switching and creating documents never copies the previous canvas into another document | `tests/e2e/document-switching.spec.ts:16:1` | Chrome 1280×720 | E3 |
| 4 | selected data set opens its values and a schema field can be dragged onto the canvas | `tests/e2e/editor-fixes.spec.ts:99:1` | Chrome 1280×720 | E4 |
| 5 | imports image into an existing document without losing pages and supports undo | `tests/e2e/editor-image-import.spec.ts:39:3` | Chrome 390×844 | E5 |
| 6 | imports pdf into an existing document without losing pages and supports undo | `tests/e2e/editor-image-import.spec.ts:39:3` | Chrome 390×844 | E5 |
| 7 | imports image into an existing template without losing pages and supports undo | `tests/e2e/editor-image-import.spec.ts:39:3` | Chrome 390×844 | E5 |
| 8 | imports pdf into an existing template without losing pages and supports undo | `tests/e2e/editor-image-import.spec.ts:39:3` | Chrome 390×844 | E5 |
| 9 | email shows partial failures, safely retries and confirms SMTP acceptance | `tests/e2e/email.spec.ts:3:1` | Chrome 1280×720 | E6 |
| 10 | mobile editor keeps slides horizontal and opens shapes from Tools | `tests/e2e/guest-editor.spec.ts:128:1` | Chrome 390×844 | E7 |
| 11 | text formatting toolbar stays outside the edited text | `tests/e2e/guest-editor.spec.ts:265:1` | Chrome 1280×720 | E8 |
| 12 | site section headings fit inside their white panels | `tests/e2e/interaction-regressions.spec.ts:34:1` | Chrome 1280×720 | E9 |
| 13 | a separate walking map keeps numbered photos and the city plan out of the main route | `tests/e2e/journey-tools.spec.ts:103:1` | Chrome 390×844 | E10 |
| 14 | route edits keep notes with their point and require selecting an edited address | `tests/e2e/journey-tools.spec.ts:237:1` | Chrome 390×844 | E11 |
| 15 | nearby visits create a daytime plan and enter the route only when requested | `tests/e2e/journey-tools.spec.ts:282:1` | Chrome 1280×720 | E12 |
| 16 | a ready Petersburg walk preserves its order and the main trip at 390px | `tests/e2e/journey-tools.spec.ts:309:31` | Chrome 390×900 | E13 |
| 17 | a ready Petersburg walk preserves its order and the main trip at 1440px | `tests/e2e/journey-tools.spec.ts:309:31` | Chrome 1440×900 | E13 |
| 18 | historic buildings can be searched and saved as a separate walk on a phone | `tests/e2e/journey-tools.spec.ts:338:1` | Chrome 390×844 | E14 |
| 19 | themed walks preserve the trip and print seven numbered photographs on a phone | `tests/e2e/journey-tools.spec.ts:365:1` | Chrome 390×844 | E15 |
| 20 | creates an actual reusable template directly and opens its editor | `tests/e2e/marketing-journey.spec.ts:29:1` | Chrome 1280×720 | E4 |
| 21 | two phones contribute to one album; organiser reorders while contributors edit only their own captions | `tests/e2e/shared-album.spec.ts:3:1` | Chrome 390×844 × 2 | E16 |
| 22 | record start-stop persists full GPS, factual report and preview across an offline reload | `tests/recording.ui.ts:48:1` | Chromium 1440×900 (desktop) | J1 |
| 23 | changing a filter removes the old discovery and a stalled source cannot keep searching forever | `tests/recording.ui.ts:238:1` | Chromium 1440×900 (desktop) | J2 |
| 24 | Moscow house card shows official technical data and restores it without another API request | `tests/recording.ui.ts:257:1` | Chromium 1440×900 (desktop) | J3 |
| 25 | ordinary map tap opens the same house profile, Citywalls paragraphs and photo | `tests/ui.spec.ts:140:1` | Chromium 1440×900 (desktop) | J4 |
| 26 | Moscow repairs appear in the shared house card and remain available after restart without source requests | `tests/ui.spec.ts:367:1` | Chromium 1440×900 (desktop) | J5 |
| 27 | Moscow house card shows official technical data and restores it without another API request | `tests/recording.ui.ts:257:1` | Chromium 393×727 (android) | J3 |
| 28 | route survives reload and hides all unrelated markers until explicitly shown | `tests/ui.spec.ts:76:1` | Chromium 393×727 (android) | J6 |
| 29 | ordinary map tap opens the same house profile, Citywalls paragraphs and photo | `tests/ui.spec.ts:140:1` | Chromium 393×727 (android) | J4 |
| 30 | Moscow repairs appear in the shared house card and remain available after restart without source requests | `tests/ui.spec.ts:367:1` | Chromium 393×727 (android) | J5 |
| 31 | Moscow house card shows official technical data and restores it without another API request | `tests/recording.ui.ts:257:1` | Chromium 360×640 (small-android) | J3 |
| 32 | route survives reload and hides all unrelated markers until explicitly shown | `tests/ui.spec.ts:76:1` | Chromium 360×640 (small-android) | J6 |
| 33 | ordinary map tap opens the same house profile, Citywalls paragraphs and photo | `tests/ui.spec.ts:140:1` | Chromium 360×640 (small-android) | J4 |
| 34 | Moscow repairs appear in the shared house card and remain available after restart without source requests | `tests/ui.spec.ts:367:1` | Chromium 360×640 (small-android) | J5 |
| 35 | Moscow house card shows official technical data and restores it without another API request | `tests/recording.ui.ts:257:1` | Chromium 740×360 (android-landscape) | J3 |
| 36 | ordinary map tap opens the same house profile, Citywalls paragraphs and photo | `tests/ui.spec.ts:140:1` | Chromium 740×360 (android-landscape) | J4 |
| 37 | Moscow repairs appear in the shared house card and remain available after restart without source requests | `tests/ui.spec.ts:367:1` | Chromium 740×360 (android-landscape) | J5 |

## Editor

| Причина | Класс | Ожидание и наблюдение | Доказательство и минимальное изменение |
| --- | --- | --- | --- |
| E1 | F | На телефоне открывается новая публикация альбома, затем сохраняются подпись и фото. В baseline `page.goto` истёк через 30 с до действий формы. | Trace остановился на навигации. Корневая причина первого холодного запуска не доказана. В полном повторе тест прошёл. Связан E16, общий E2E-ресурс шрифтов локализован. |
| E2 | B | Canvas меняет фон и экспортирует его без потери разрешения. В baseline проверка хеша Canvas упала, затем ожидание скачивания истекло. Новый trace воспроизвёл блокировку кнопки overlay боковой панели. | Закрываем панель после выбора и открываем `details.toolbar-more`. Повтор проверил изменение хеша, PNG, исходный размер и равенство пикселей. Связаны E3, E5. |
| E3 | B | A и B открывают свои версии Canvas. Новый C пуст и получает только свои записи. В baseline тест остановился перед первым скачиванием JSON. | Открываем меню и боковую панель документов. В повторе холодная загрузка заняла 24 с из 30, A и B сравнились, C начал загружаться перед дедлайном. Локальный лимит 45 с оставляет время на третий документ и проверку PUT. Связаны E2, E5. |
| E4 | B | Видны значения модели и выбор схемы. Поле перетаскивается, выбранная схема сохраняется. В обоих traces панель скрыта при 1280 px. | В компактном режиме панель раскрывается кнопкой свойств. Для drag-and-drop тест использует 1728×960 с обеими видимыми областями. Проверка создания шаблона открывает вкладку данных в компактном режиме. Связаны E3, E5 и тесты генерации. |
| E5 | B | Изображение/PDF в шаблоне и документе добавляет слайды. Отмена и undo/redo сохраняют исходную страницу. Исходный trace показывает доступный input внутри dialog, locator ждёт region. | Обновлена роль и открывается меню JSON. Повтор PDF показал, что текущий импорт обрабатывает все страницы. Теперь проверяются HTTP-последовательность 1, 1, 2, количество слайдов, размеры, исходная страница, данные изображения/распознавания, undo и redo. Связаны E2, E3. |
| E6 | C | При частичном отказе письмо можно повторить с прежним request ID и увидеть подтверждение SMTP. Кнопка preview не находилась, запрос отправки не состоялся. | `FEATURE_EMAIL` управляется Vite, действие в закрытом details. Включён флаг тестового сервера, открывается меню. SMTP представлен существующей fixture частичного ответа. Связан E3. |
| E7 | B | Горизонтальная лента и инструменты фигур доступны на телефоне. Layout-проверки прошли до устаревшего поиска кнопки /Tools/. | Видимая кнопка CanvasDock имеет доступное имя Tool library. Фикстура использует это имя. Связан `canvas-cell-editing.spec.ts`. |
| E8 | B | Inline-редактор открывается на тексте, панель форматирования находится вне текста. Trace при масштабе 200% не нашёл overlay после абсолютного двойного клика. | Берём bounds `.konvajs-content` и выполняем двойной клик через locator с автоматической прокруткой к нужной позиции. Целевой повтор прошёл исходную проверку геометрии. Связаны E7 и тесты редактора ячеек. |
| E9 | B | Заголовок каждой секции сайта внутри своего поля. Тест ожидал 5 секций, DOM содержит 6. | Число обновлено до 6. Геометрия проверяется для всех рендерящихся секций. Связаны остальные UI-сценарии `interaction-regressions.spec.ts`. |
| E10 | B | Отдельная прогулка содержит две пронумерованные фотографии, а основной маршрут не меняется. Trace завис на Generate a walking map. | `RoutePlacePanel` открывает iframe Journey. Старой команды нет. Контракт `privateWalk.ts` передаёт точки и геометрию без фото. Нужен согласованный переход сценария и покрытие передачи/печати фотографий. Связаны E12–E15. |
| E11 | B | Перестановка сохраняет заметки, неподтверждённый адрес закрывает инструменты. После Escape тест ожидал отсутствие панели у восстановленного адреса, DOM содержит одну панель. | Проверяется отсутствие при `aria-invalid=true` и возврат после Escape. Backend использует OSM при отсутствии 2GIS, поэтому отказ запроса гостиниц проверяется поддерживаемым ответом 503. Тест сохраняет проверки заметок, уведомления об отказе и альтернативной ссылки. Связаны E10, E12. |
| E12 | B | Ближайшие визиты формируют дневной план, а точки входят в маршрут после явного подтверждения. Trace ждёт Generate a walking map. | Общая причина E10. Потребуется сценарий выбора в Journey и отдельная проверка сохранения дневного плана и основного маршрута. Связаны E10, E13. |
| E13 | B | Готовая прогулка сохраняет исходный порядок и основной маршрут на двух ширинах. Trace ждёт старую ссылку Ready-made walks in Saint Petersburg внутри остановки. | Каталог входит в Journey. В `tests/ui.spec.ts:90` проверяются выбор, четыре точки и restart. Для сквозной замены нужна связка реального каталога и приёмника CoDoc на обеих ширинах. Связаны E10, E12, J6. |
| E14 | B | Поиск исторических зданий добавляет отдельную прогулку на телефоне. Trace ждёт select Walk theme внутри старой панели. | Источник и управление перенесены в Journey. Перенести fixture и проверку поиска, затем проверить handoff. Связаны E10, E15. |
| E15 | B | Тематическая прогулка сохраняет основной маршрут и печатает семь фотографий. Trace ждёт старый Walk theme. | Совпадает путь отказа E14. Требуется отдельный согласованный контракт фото и атрибуции между Journey и документом. Проверку количества фото сохранено в исходном тесте. |
| E16 | B | Организатор и участник на двух телефонах меняют свои подписи и порядок в рамках ролей. Тест остановился с ENOENT шрифта. | Путь относился к старому физическому положению Java-модуля. Для E2E используется валидный шрифт из самого Editor. Условия владения сохранены. Связан E1. |

## Journey

| Причина | Класс | Ожидание и наблюдение | Доказательство и минимальное изменение |
| --- | --- | --- | --- |
| J1 | E | Три GPS-точки сохраняются в IndexedDB, план, истории и изображение переживают offline reload. Desktop в baseline и первом повторе истёк до появления `.recording-list__open`. Мобильные повторы и новый desktop-запуск прошли. | В desktop trace сценарий остановился до запроса обогащения и чтения отчёта. Причина различия по времени не установлена. Сохранены GPS fixture, проверки данных и offline reload. При повторе сбоя измерить время завершения stop и commit IndexedDB. Связан J2. |
| J2 | E | Старые маркеры исчезают при смене фильтра, stalled request заканчивается ошибкой и открывает «Повторить». В baseline desktop остался статус поиска. Первый повтор упал на landscape, полный прогон — на small Android. | Тест теперь ждёт фактический `/api/walks/v1/places/themed` после debounce и до перемотки часов. После правки два повтора на всех четырёх viewport прошли вместе с GPS и восстановлением, 24/24. Связан J1. |
| J3 | C | Официальные этажи и проект отображаются и восстанавливаются из кеша. Во всех viewport отсутствовал `.building-facts`. Trace не содержит вызовов apidata. | `MoscowDataClient.get` требует ключ до transport. Синтетический ключ задаётся Vite из Playwright. Установленная fixture перехватывает запрос, счётчик API остаётся в проверке. Связан J5. |
| J4 | B | Тап на конкретный дом показывает паспорт, абзацы и полное фото Citywalls. В четырёх traces был reverse и OSM, а прямого запроса `/house1709.html` нет. | Тест менял индекс после открытия карты и кликал непроверенный центр восстановленного маршрута. Сценарий начинает карту с фиксированного якоря, перехватывает индекс и паспорт, кликает рядом с якорем внутри радиуса сопоставления. Проверки фото и паспорта сохранены. Связаны J5, J6. |
| J5 | C | Ремонты показаны в общей карточке и сохраняются после перезагрузки без новых запросов. Элемент «Вода и канализация» отсутствовал на всех ширинах. | Та же конфигурация клиента, что и в J3. Проверяется отдельный fixture ремонта и нулевой прирост запросов после restart. |
| J6 | A | Две выбранные точки видны после загрузки маршрута, третья показывается только по нажатию. В исходных traces 393/360 px виден один маркер, а список маршрута содержит оба. Скриншот показывает обрезанный слева маршрут. | Эффект Leaflet удаляет экземпляр карты и оставляет `fittedRouteRef`. Следующий экземпляр пропускает fitBounds с тем же объектом маршрута. Очистка сбрасывает ссылку. Имеющийся тест падает до правки и проверяет два маркера, переключатели, reload и сохранённую геометрию после неё. Связаны E13, J4. |

## Источники прогона

В полном повторе Editor дополнительно обнаружен отказ `journeys.spec.ts:4` (класс B), которого не было в 37 исходных записях. Публикация возвращала 200, сохранение ответа 204, а fixture статуса возвращала карточку документа вместо состояния публикации. Browser trace содержит ошибку вызова `toLowerCase()` у отсутствующего `state`. Тест возвращает корректный статус из fixture, сохраняет проверку заголовка доступа и видимости ответа владельцу. Два повтора файла дали 4/4 в `editor-rsvp-retry.log`.

- Phase 1. `C:/sandpr/.cache/architecture-phase1-20261125/{logs,ticket-editor/test-results,private_jorney/test-results}`.
- Phase 1.1. `C:/sandpr/.cache/quality-recovery-20261126/logs`, включая `traces.txt`, `phase1-import.jpg`, `phase1-mobile-route.jpg`, целевые и полные Playwright-логи.
- Сводка и ограничения в [BASELINE-AFTER](BASELINE-AFTER.md).

## Phase 1.1b — шесть перенесённых записей

В `ticket-editor/tests/e2e/journey-tools.spec.ts` шесть записей переведены на действующий вход через iframe. Для отдельных проверок приёмника используется контролируемый отправитель, который запускается в iframe и отправляет реальное сообщение. Профиль built handoff в `trip-walk-handoff.spec.ts` загружает оба frontend-билда и использует кнопку отправки самого Journey. [Браузерные и backend-прогоны](BASELINE-AFTER.md).

| Запись | Проверки после переноса |
| --- | --- |
| E10 | Отдельная карта с двумя фото. Номера связаны с исходными точками. Остановки основного маршрута сохранены. |
| E12 | Визит с частичным недельным расписанием строит дневной план на выбранную дату. План входит в заметки только по кнопке. Список остановок остаётся прежним. |
| E13, две записи | На 390 и 1440 px порядок точек принимается без перестановки. В Journey каталог отдельно проверяет порядок четырёх сохранённых POI при открытии и после reload. |
| E14 | Journey на четырёх профилях отправляет запрос поиска исторического дома с архитектором и годом и сохраняет выбранный результат. CoDoc принимает отдельную прогулку на телефоне и сохраняет год, архитектора и ссылку на источник. |
| E15 | Тематическая прогулка передаёт семь нумерованных фото, связанных с точками. Основной маршрут сохранён. Java-тесты разбирают PDF и проверяют текст, атрибуцию, изображение и продолжение документа при отклонении фото. |

Дополнительные причины, обнаруженные в работе. **A**: расписание в Zod требовало все семь ключей, поэтому единственный валидный день не появлялся в плане. **A**: планировщик удалял день с присоединённой прогулкой, если в нём не было города, обычных остановок или транспорта. **B**: один Travel-тест до явного подтверждения переходил на следующий день, а built-тест искал черновик в анонимном хранилище при активном профиле владельца. **B**: тест панели остановки ожидал сохранение сразу по postMessage. Все улучшенные проверки сохраняют утверждения об исходном поведении.

В built handoff при повторном открытии день ищется по дате. API сохраняет только заполненные дни, поэтому у поездки с прогулкой во втором календарном дне может быть одна запись в списке. Проверены версия после PUT и заметка после reload. В Journey синтетическое ожидание недоступного поиска начинается до навигации. Fixture закрывает отложенный запрос после проверок, включая неуспешный тест. GPS offline reload дожидается DOM и отдельно проверяет данные и карту. Профили и логи повторов приведены в [BASELINE-AFTER](BASELINE-AFTER.md).
