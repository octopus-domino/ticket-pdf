# Phase 1.2. Failure Mode Analysis

| Отказ | Ожидаемый контракт и наблюдение | Область доказательства |
| --- | --- | --- |
| БД или обязательная таблица отсутствует | PostgreSQL отключён на время проверки обоих сервисов. Readiness перестал проходить, liveness остался 200. Переименование обязательной таблицы дало readiness 503, возврат таблицы и запуск БД восстановили readiness. | Linux Docker PostgreSQL smoke и `DocumentFlowTest.readinessRejectsMissingSchemaAndRecoversWithoutChangingLiveness` на H2. |
| Redis остановлен | Запрос без ключа и запрос со старым ключом получают 401 и 403. | Отдельный Tyk workload для 160 и 384 МиБ. |
| Routing остановлен | Document стартует, авторизует синтетический аккаунт, сохраняет документ, схему, данные, шаблон и выдаёт PDF. При включённом Travel PDF поездки вернул 503. При выключенном Travel диагностирован прежний 500 от отсутствующего Document endpoint, ARC-024. | Серверные H2/PostgreSQL smoke и проверка HTTP клиентов. |
| Document остановлен | Routing стартует, категории прогулок доступны. При включённом Travel список поездок возвращает 503. После запуска Document доступ через прежний тестовый bearer восстановился. При выключенном Travel список отвечает 404. | Серверные H2/PostgreSQL smoke. |
| Поставщик маршрутов зависает | Локальный BRouter не ответил. Запрос маршрута вернул 503 за 12,09 с. Readiness и каталог тем оставались доступны. | Те же JAR, Windows/H2 и локальный HTTP double. |
| SMTP | Клиент прервал ожидание через 0,5 с. SMTP не ответил в срок 1,8 с. Повтор с тем же request ID вернул 202 с `sent=0, failed=1` за 142 мс. SMTP зафиксировал одно подключение. | Windows/H2 и локальный silent SMTP. Реальная доставка проверяется отдельно. |
| OCR | Без Tesseract запрос вернул 200 за 172 мс с `recognition.empty_result`. Readiness остался 200. Полный отказ пока неотличим от изображения без текста, ARC-025. | Windows/H2 с отсутствующим бинарным файлом. |
| Прерывание фоновой генерации | После принудительной остановки JVM и перезапуска владелец увидел `failed`, выдача результата вернула 409, запрос с некорректным bearer вернул 401. Частичный результат недоступен. | Windows/H2 с принудительным завершением выделенной JVM и тест восстановления `GenerationRegressionTest.interruptedJobsAreTerminalAfterRestart`. |

Полный прогон передачи полномочий и restore описан в [baseline](BASELINE-AFTER.md). Ошибка readiness повторно проверяется через восстановление исходного состояния, а причины отказов, влияющие на другие модули, ведутся в [backlog](ARCHITECTURE-BACKLOG.md).

Лог локального теста с собранными JAR `native-faults-http.log`. Испытания дополнительных отказов в Linux Docker можно повторить после восстановления SSH. На хосте с JDK 17 и локальным Docker:

```bash
javac --release 17 -d /tmp/codoc-fixtures ticket-infra/scripts/tests/fixtures/OperationalUpstream.java
python3 ticket-infra/scripts/operational_faults.py --document-jar ./document.jar --routing-jar ./routing.jar --upstream-class /tmp/codoc-fixtures/OperationalUpstream.class
```
