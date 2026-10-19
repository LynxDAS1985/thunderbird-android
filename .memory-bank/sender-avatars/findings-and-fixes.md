# Замечания и исправления по аватарам отправителей

Дата фиксации: 2026-10-10. Последняя сверка: 2026-10-19. Этот документ хранит результаты независимой проверки рабочей версии. Он не заменяет [план](implementation-plan.md), [текущий статус](implementation-status.md) и [проверку на телефоне](phone-verification.md).

## Правило статуса

- `Исправлено` — решение реализовано и указан успешный способ проверки.
- `Частично` — безопасное ядро реализовано и проверено, но пользовательский путь ещё не подключён полностью.
- `Запланировано` — причина подтверждена, решение выбрано, но код ещё не изменён.
- `Требует телефона` — автоматическая часть готова, однако пользовательский сценарий не подтверждён на устройстве.

## N1-N12

| № | Статус | Что найдено | Как решаем и почему | Что должно быть в итоге | Как проверить |
|---|---|---|---|---|---|
| N1 | Исправлено | Флаг `enable_sender_avatar_settings` был объявлен, но рабочий код его не читал | `GeneralSettingsFragment` теперь выбирает unified/legacy режим в одной точке. При выключенном или недоступном флаге остаются прежние пункты, поэтому аварийное отключение не удаляет значения | Debug открывает единый раздел; release и недоступный флаг сохраняют старые пути | `SenderAvatarPreferenceModeTest`, `:legacy:ui:legacy:testDebugUnitTest` |
| N2 | Исправлено | Единого `SenderAvatarResolver` не было; разные места могли выбирать изображение по-разному | Resolver подключён к трём реализациям списка, открытому письму, участникам и одиночным уведомлениям. Каждый адаптер передаёт аккаунт, папку и UID | Одно письмо проходит одинаковый выбор источника во всех местах | Целевые тесты `MessageListSenderAvatarResolverTest`, `MessageViewSenderAvatarResolverTest`, `K9NotificationResourceProviderTest`; телефон ещё требуется |
| N3 | Исправлено | Зелёный щит не был привязан к доказательству подлинности конкретного письма | Resolver принимает щит только при точном совпадении сообщения и непустом доказательстве (DMARC pass + BIMI assertion). UI (`MessageHeader`, `MessageTopView`) проверяет статус и включает badge только для валидного доказательства | Контакт, fallback, ошибка и доказательство другого письма не получают щит; показ щита активируется только при строгой DMARC/BIMI валидации | Отрицательные unit-тесты resolver и Android-адаптеров; перед выпуском сценарии `VERIFY` |
| N4 | Исправлено | Общий менеджер настроек был зарегистрирован, но экран продолжал использовать старый доступ | `GeneralSettingsDataStore` читает и меняет три avatar-поля через единый контракт. Старые ключи и старые внутренние managers сохранены как источник хранения | Интерфейс и рабочая логика читают одинаковые значения без миграции ключей | Поиск потребителя, JVM-тест manager, сборка legacy UI |
| N5 | Исправлено | Две части настроек сохранялись в разное время; сразу после записи было видно смешанное состояние | Manager публикует выбранную конфигурацию сразу и удерживает её, пока оба старых хранилища не подтвердят то же состояние. При сбое откатывает состояние | После `save` пользователь видит один согласованный набор, даже если уведомления записываются позже | Тест `save should not expose mixed state while notification save is delayed` |
| N6 | Исправлено | `showContactPictureInNotification` отсутствовал в резервной копии и импорте | Существующий storage-ключ зарегистрирован в `GeneralSettingsDescriptions` и поддержан upgrader-ом до версии 112 | Значение экспортируется и импортируется, а старый backup без ключа корректно мигрирует | `:legacy:core:testDebugUnitTest` и полный тест импорта перед выпуском |
| N7 | Требует телефона | Тест восьми комбинаций проверяет модель в памяти, но не обновление старого APK | Сохранить быстрый unit-тест и дополнить его установкой старого APK с последующим обновлением новым. Только устройство проверяет настоящее Android-хранилище | Аккаунты и три старые настройки не сбрасываются после обновления | Сценарии `MIGRATE-01`–`MIGRATE-07` с версией APK и доказательством |
| N8 | Запланировано | APK в `distrib` является debug-сборкой, подписан тестовым сертификатом и игнорируется Git | Оставить debug APK только для локальной проверки; для продажи собрать release с ключом владельца и публиковать через отдельный канал артефактов | Тестовая и коммерческая сборки явно различаются; release обновляется тем же ключом | `apksigner verify --print-certs`, установка и обновление release APK |
| N9 | Исправлено | Проверка памяти находила 12 пропущенных регистраций и 13 битых ссылок; Markdown-проверка не проходила | Зарегистрировать все страницы в двух индексах, заменить ссылки на отсутствующие цели честным текстом и применить формат проекта | `Check-Memory.ps1` сообщает 0 ошибок, Markdown соответствует Spotless | `scripts/project/Check-Memory.ps1`, `gradlew spotlessCheck`, `git diff --check` |
| N10 | Исправлено | BIMI-модуль не входил в зависимости приложения и не имел DI-регистрации | Добавлены прямые зависимости API/internal в `app-common` и один `BimiLogoRepository` с приватным cacheDir приложения согласно ADR-0009 | Безопасный загрузчик и SVG→PNG входят в APK и доступны потребителю | `:legacy:core:compileDebugKotlin` |
| N11 | Исправлено | При обновлении SVG мог остаться PNG от предыдущего логотипа | Перед записью нового SVG старый PNG удаляется; повреждённая пара очищается вместе с PNG | После изменения логотипа старое изображение не возвращается | `BimiDiskCacheTest`, 38 тестов модуля |
| N12 | Исправлено | Нельзя доверять любому присланному `Authentication-Results` | Verifier требует явно доверенный `authserv-id`, DMARC pass, совпадение From и точную личность письма; производит unfolding заголовков и очистку комментариев | Поддельный заголовок не даёт щит; поддержана сквозная интеграция с аккаунтом | `DefaultDmarcVerifierTest`, `:core:preference:impl:jvmTest` |

---

## Исправления дефектов независимого аудита (CRIT-01..13, RISK-01..08)

### Критические дефекты (🔴)

| Код | Статус | Компонент | Проблема | Решение | Проверка |
|---|---|---|---|---|---|
| CRIT-01 | Исправлено | `legacy/core`, `app-common` | Нарушение ADR-0009: `legacy:core` напрямую зависел от `:feature:mail:sender:avatar:internal` | Зависимость `:internal` удалена из `legacy:core`. Фабрика `BimiLogoRepository` перенесена в `app-common` (`FeatureMailModule.kt`). В `legacy:ui:legacy` подключен только `:api` | Gradle build, отсутствие утечки internal типов |
| CRIT-02 | Исправлено | `legacy/core/preferences` | `Settings.VERSION` не был инкрементирован при добавлении `showContactPictureInNotification`, отсутствовал upgrader | `Settings.VERSION` повышен до 112. Создан `GeneralSettingsUpgraderTo112.java`, зарегистрирован в `GeneralSettingsDescriptions.java` | `GeneralSettingsDescriptionsTest` проходит успешно |
| CRIT-03 | Исправлено | `BimiDiskCache.kt` | Отсутствие синхронизации потоков, гонки при `prune()`, падение при чтении `file.length()`, `Stream<Path>` concurrent modification | Добавлена `@Synchronized` блокировка, безопасный `safeFileSize()`, сбор в `.toList()` перед удалением, отслеживание псевдонимов `writeAlias`/`findCachedPng` | Модульные тесты `BimiDiskCacheTest` |
| CRIT-04 | Исправлено | `DefaultBimiLogoRepository.kt` | Блокирующий I/O в UI потоке; отсутствие быстрого синхронного метода для ViewHolders списков | `load()` переведён на `suspend fun withContext(ioDispatcher)`. Добавлен мгновенный синхронный метод `findCached(domain)` для отрисовки из кэша (0ms) | `MessageListSenderAvatarResolverTest`, UI тесты |
| CRIT-05 | Исправлено | `BimiRecordResolver.kt` | `NX_DOMAIN` маппился в `Failed`, из-за чего не срабатывал 15-минутный negative cache | `NX_DOMAIN` явно маппится в `BimiRecordLookupResult.NotFound`, что активирует 15-минутный отрицательный кэш | Unit-тесты `BimiRecordResolver` и `DefaultBimiLogoRepository` |
| CRIT-06 | Исправлено | `BimiRecordResolver.kt` | Многострочные TXT записи (RFC 1035) теряли части после первой строки | Сборка TXT через `txtRecord.characterStrings?.joinToString("") ?: txtRecord.text` | Парсер успешно собирает фрагментированные TXT |
| CRIT-07 | Исправлено | `BimiRecordResolver.kt` | Self-asserted BIMI с пустым `a=;` или отсутствующим `a=` отклонялся как ошибка | Регулярное выражение и парсер обновлены для поддержки пустого `a=` (`a=(?<authority>[^;]*);?`) | `BimiRecordParserTest` (`a=;` test case) |
| CRIT-08 | Исправлено | `StrictSvgValidator.kt` | Запрет всех `url(...)` блокировал валидные линейные и радиальные SVG градиенты (`url(#gradient)`) | Разрешены локальные ссылки `url(#id)`, строго запрещены внешние схемы (`http`, `https`, `data`, `//`) | `StrictSvgValidatorTest` с локальными градиентами |
| CRIT-09 | Исправлено | `SafeBimiTransport.kt` | Падение `IllegalArgumentException` при некорректном URL в заголовке `Location` редиректа | Редирект обёрнут в `runCatching` с возвратом контролируемой ошибки загрузки | Изолированный тест сетевого транспорта |
| CRIT-10 | Исправлено | `DefaultDmarcVerifier.kt` | RFC 5322 multiline folding (`\r\n\t`) и комментарии `(...)` ломали извлечение `authserv-id` и `dmarc=pass` | Реализован unfolding многострочных заголовков и удаление комментариев перед разбором токенов | `DefaultDmarcVerifierTest` |
| CRIT-11 | Исправлено | `DefaultDmarcVerifier.kt` | Не выставлялся метод `BIMI_ASSERTION`, из-за чего `mayShowShield` всегда возвращал `false` | При наличии `bimi=pass` в доверенном заголовке добавляется `SenderVerificationMethod.BIMI_ASSERTION` | `DefaultDmarcVerifierTest` |
| CRIT-12 | Исправлено | `MessageTopView`, `MessageHeader`, `MessageListSenderAvatarResolver` | Отсутствовало сквозное подключение BIMI и DMARC к UI списков и просмотрщика писем | Внедрён `BimiLogoRepository` в списки и просмотрщик. В `MessageTopView` подключён DMARC verifier, мгновенный показ кэшированного логотипа и асинхронная подгрузка из сети. В `MessageHeader` добавлен `sender_verified_badge` | Прохождение тестов `MessageListSenderAvatarResolverTest`, `MessageViewSenderAvatarResolverTest` |
| CRIT-13 | Исправлено | `ContactPictureLoader.kt` | Заглушка контакта использовала иконку галочки `Icons.Outlined.Check`, вводя в заблуждение о «верификации» | Иконка заменена на нейтральную `Icons.Outlined.AccountCircle` | Визуальная корректность заглушек |

### Устранённые риски (🟡)

| Код | Статус | Компонент | Проблема | Решение |
|---|---|---|---|---|
| RISK-01 | Исправлено | `DefaultSenderAvatarSettingsPreferenceManager.kt` | Сохранение настроек могло оставить модель в памяти в рассинхронизированном состоянии при сбое | Добавлен `try/catch` с откатом `configState` и очисткой `pendingConfig` |
| RISK-02 | Исправлено | `SenderAvatarSettings.kt` | Настройка показа аватаров в письме не была отделена от списка писем | Добавлено независимое свойство `isShowInMessageView: Boolean = true` |
| RISK-03 | Исправлено | `BimiRecordResolver.kt` | Не поддерживался тег селектора `s=` в TXT-записях BIMI | Добавлен парсинг тега `s=` |
| RISK-04 | Исправлено | `BimiDiskCache.kt` | Отсутствие отслеживания псевдонимов доменов | Реализованы `writeAlias` и `findCachedPng` |
| RISK-05 | Исправлено | `DefaultBimiLogoRepository.kt` | Риск «stampede» при одновременных запросах к отсутствующим логотипам | Отрицательный кэш 15 минут гарантирует отсутствие повторных DNS/HTTP запросов |
| RISK-06 | Исправлено | `DefaultDmarcVerifier.kt` | Чувствительность к регистру доменов и `authserv-id` | Приведение к нижнему регистру при нормализации |
| RISK-07 | Исправлено | Все компоненты | Риск утечки PII (email-адресов) в логи через Timber | Проверены все вызовы логов, email адреса нигде не выводятся |
| RISK-08 | Исправлено | `BimiDiskCache.kt` | Утечка файловых дескрипторов при `Files.walk` / `Files.list` | Использование `.use { }` и `.toList()` перед итерацией |
