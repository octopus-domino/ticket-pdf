# Backend stabilization — 2026-11-03

Полная карта F01–F36 находится в `ticket-editor/STABILIZATION.md` (соседний репозиторий). Исходные пользовательские изменения сохранены коммитом `43905fc` и отправлены до рефакторинга.

Генерация разделена на прикладной сценарий и заменяемые порты каталога, rendering, packaging и storage. Async worker является отдельным Spring bean. Дисковые результаты и metadata записываются атомарно в `app.generation.storage-dir` (по умолчанию `./data/generation`), с ограничениями 100 MB/result и 1 GB суммарно. После restart completed jobs доступны, прерванные помечаются failed. Cleanup удаляет истёкшие задания и файлы; отмена проверяется между элементами и перед публикацией, а не прерывает произвольную операцию PDF посередине.

`mvn -B verify` запускает unit/security/async/MIME/delivery/PDF и полный HTTP integration test, затем собирает executable JAR. Tests используют in-memory H2 и временные файлы. Рабочая база `data/ticket-db.mv.db` сохранена локально и исключена из Git вместе с runtime data.

## Production и данные

Активируйте профиль `prod`, задайте собственный `JWT_SECRET` и OAuth/SMTP environment variables. H2 console и mail debug отключены; refresh cookie читает `app.cookie-secure`. Production использует `ddl-auto=validate`: ожидается существующая бизнес-схема БД. Аддитивный `db/upgrade-email-ledger.sql` создаёт только ledger и выполняется перед JPA validation. Он не создаёт весь проект с нуля и не заменяет baseline migration при первом развёртывании.

Перед обновлением остановите приложение и сделайте согласованную копию всего data volume, включая H2 и generation metadata/results. Восстановление выполняется в отдельный пустой volume при остановленном приложении, затем проверяются login, CRUD и download до переключения на него. Не копируйте работающую H2 как обычный файл и не запускайте две независимые JVM на одной файловой БД. Это решение рассчитано на один экземпляр приложения; масштабирование требует общей БД, distributed queue и общего object storage.

## Контракты и границы

- JSON body ≤20 MB; synchronous generation ≤100 items; async ≤10000; ≤2 active jobs/user, executor 2 threads и очередь 8.
- Formats `svg`, `pdf`, `pdfa`; packaging `zip`, `single`, `multipage-pdf`. SVG нельзя упаковать как PDF. Single требует ровно один итоговый документ.
- `filenamePattern` разрешает вложенные пути. Sync errors доступны как Base64 UTF-8 JSON в `X-Generation-Errors` (первые 10, текст ограничен); полные item errors фоновой job — в status JSON. Заголовок дополнен count/failed и открыт через CORS.
- `embedImages=false` поддержан для SVG. PDF/PDF-A требует `true`, чтобы сетевые обращения шли только через защищённый downloader. `prettyPrint` применяется к SVG; PDF version поддерживается только `pdf/a-2b`.
- Downloader проверяет реальные DNS addresses при соединении, блокирует private IP, redirects и SVG-вложения, ограничивает время/размер raster download. Итоговый SVG очищается allowlist sanitizer.
- Email использует первый слайд. `requestId` обязателен для SMTP. Повтор того же payload пропускает SENT; PENDING/UNKNOWN не отправляется автоматически повторно, поскольку результат SMTP после разрыва соединения может быть неизвестен. Изменённый payload создаёт новую delivery identity.
- EML включает получателей. Вложенные PNG/CID и plain text сохраняют текущую модель email, это не editable HTML layout.
- `/actuator/metrics` требует авторизации; доступны `ticket.generation.jobs` (status tag) и `ticket.generation.duration`. Логи не следует включать в DEBUG на production.

PDFBox regression проверяет страницы, XMP и OutputIntent после merge, но не заменяет veraPDF. Реальный SMTP, performance acceptance, Docker runtime и новые production deployment не выполнялись.
