# Почта, синхронизация, отправка, хранение и криптография

Русская карта для работы с `backend/`, `mail/`, `legacy/`, `library/`. Срез локальных исходников: 2026-10-09. Исходники не перемещаются. Ссылки отсчитываются от этого документа; команды выполняются из корня репозитория.

Это навигация по существующему коду, а не аудит безопасности или подтверждение работоспособности приложения. Ни сборки, ни тесты Android, ни публикации в этой задаче не запускались. Степень чтения указана в конце: наличие ссылки само по себе не означает полного чтения файла.

## 1. Границы и полный перечень крупных каталогов

Ниже перечислены все непосредственные модули этих четырёх областей, включая вложенные `mail/protocols/*` и `legacy/ui/*`. Это полный перечень на уровне модулей, не рекурсивный список каждого ресурса и Java/Kotlin-пакета. Фактические Gradle-включения проверять в [settings.gradle.kts](../../settings.gradle.kts).

| Каталог | Назначение и куда смотреть |
|---|---|
| [backend/api](../../backend/api/) | Контракт `Backend`, возможности протокола, `BackendStorage`, `BackendFolder`, `SyncConfig`, `SyncListener`; также фабрики в пакете `net.thunderbird.backend.api` |
| [backend/imap](../../backend/imap/) | Согласование серверных IMAP-писем с локальными данными, команды папок/флагов/перемещения/загрузки, push; отправка делегируется SMTP |
| [backend/pop3](../../backend/pop3/) | POP3-синхронизация, скачивание полного сообщения, ограниченный набор серверных действий; отправка через SMTP |
| [backend/jmap](../../backend/jmap/) | JMAP-команды поверх `rs.ltt.jmap` и OkHttp; присутствуют явно не реализованные операции |
| [backend/demo](../../backend/demo/) | Демонстрационный ящик и письма в `src/main/resources/mailbox`; собственные команды sync/send, не настоящий SMTP-сервис |
| [backend/testing](../../backend/testing/) | Переиспользуемые `InMemoryBackendStorage` и `InMemoryBackendFolder` в `src/main`, а не самостоятельный протокол |
| [mail/common](../../mail/common/) | `Message`, `Part`, MIME, адреса, тела, потоки, настройки сервера, OAuth/SASL и интерфейсы TLS |
| [mail/protocols/imap](../../mail/protocols/imap/) | Низкоуровневые IMAP-соединения, команды, ответы, папки, FETCH и IDLE |
| [mail/protocols/pop3](../../mail/protocols/pop3/) | POP3-соединение, capabilities, UID/номера сообщений, получение и удаление |
| [mail/protocols/smtp](../../mail/protocols/smtp/) | SMTP-транспорт, согласование расширений, аутентификация, передача MIME, обработка ответов |
| [mail/testing](../../mail/testing/) | Создание тестовых MIME-писем, проверки содержимого, тестовые socket/key-store реализации |
| [legacy/common](../../legacy/common/) | Android-связывание legacy: фабрики backend, OAuth-провайдер, ресурсы, уведомления и аккаунты |
| [legacy/core](../../legacy/core/) | Контроллер почты, фоновые задания, legacy mailstore, сборка писем, Autocrypt, crypto, уведомления, поиск |
| [legacy/crypto-openpgp](../../legacy/crypto-openpgp/) | Определение зашифрованной структуры и извлечение метаданных шифрования для хранения; не весь OpenPGP-процесс |
| [legacy/di](../../legacy/di/) | Legacy-доступ к DI через `app.k9mail.legacy.di.DI` |
| [legacy/logging](../../legacy/logging/) | Обёртка `Log`; исходники в `src/commonMain`, тесты в `src/commonTest`, а не `src/main` |
| [legacy/mailstore](../../legacy/mailstore/) | Контракт `MessageStore`, менеджер/фабрика, модели сохранения, доступ к полям и уведомления об изменениях |
| [legacy/message](../../legacy/message/) | Общие контракты контроллера/слушателей, `MessageReference`, `PreviewResult` |
| [legacy/storage](../../legacy/storage/) | Конкретные SQLite-операции сообщений и папок, файлы частей, схема и миграции, legacy preferences и notification store |
| [legacy/ui/base](../../legacy/ui/base/) | Базовый legacy UI; смежная область, не реализация почтового протокола |
| [legacy/ui/folder](../../legacy/ui/folder/) | Legacy UI папок; точка перехода для задач интерфейса папок |
| [legacy/ui/legacy](../../legacy/ui/legacy/) | Старые экраны, редактор письма и helper расшифровки; здесь также находятся важные crypto-тесты |
| [library/html-cleaner](../../library/html-cleaner/) | Подготовка HTML к показу через Jsoup: очистка head/body и добавление оформления |
| [library/token-auto-complete](../../library/token-auto-complete/) | UI токенов автодополнения; не backend, не хранилище и не криптография |

Основные Java-пакеты внутри `legacy/core/src/main/java/com/fsck/k9/` перечислены полностью на один уровень: `autocrypt`, `backend`, `controller`, `crypto`, `helper`, `job`, `mailstore`, `message`, `notification`, `power`, `preferences`, `provider`, `search`, `service`. Это помогает не искать всё только в `mailstore`.

В `mail/common/src/main/java/com/fsck/k9/mail/`: `filter`, `folders`, `helper`, `internet`, `message`, `oauth`, `power`, `server`, `ssl`; классы модели лежат также непосредственно в `mail`. SASL находится рядом, в `com/fsck/k9/sasl`.

В `legacy/storage/src/main/java/com/fsck/k9/` два крупных направления: `preferences` и `storage`. Для писем нужны `storage/messages`, `storage/migrations`, `storage/notifications`; миграции настроек в `preferences/migration` не следует путать с миграциями базы писем.

## 2. Главные точки входа и связывание

| Вход | Путь и роль |
|---|---|
| Публичные почтовые операции legacy | [MessagingController.java](../../legacy/core/src/main/java/com/fsck/k9/controller/MessagingController.java): `synchronizeMailbox`, `sendMessage`, `sendPendingMessages`, pending-команды и обработка результатов |
| Фоновая периодическая проверка | [MailSyncWorker.kt](../../legacy/core/src/main/java/com/fsck/k9/job/MailSyncWorker.kt): проверяет настройки фоновой работы, аккаунт и наличие данных входа, вызывает `performPeriodicMailSync`; возвращает success/retry по результату |
| Выбор backend аккаунта | [BackendManager.kt](../../legacy/core/src/main/java/com/fsck/k9/backend/BackendManager.kt): фабрика по `incomingServerSettings.type`, кеш на аккаунт, проверка актуальности входящих и исходящих настроек |
| Регистрация фабрик | [backends/KoinModule.kt](../../legacy/common/src/main/java/com/fsck/k9/backends/KoinModule.kt): `imap`, `pop3` плюс DI-карта `developmentBackends` |
| Сборка IMAP + SMTP | [ImapBackendFactory.kt](../../legacy/common/src/main/java/com/fsck/k9/backends/ImapBackendFactory.kt): `DefaultImapBackendFactory` создаёт backend storage, IMAP store, push config, SMTP transport и при нужном типе авторизации OAuth token provider |
| Общий договор с протоколом | [Backend.kt](../../backend/api/src/main/java/com/fsck/k9/backend/api/Backend.kt): sync/download/send/upload и признаки `supports*`; наличие метода не означает поддержку всеми реализациями |
| Мост к локальным письмам | [K9BackendFolder.kt](../../legacy/core/src/main/java/com/fsck/k9/mailstore/K9BackendFolder.kt): server ID папки превращается в локальный folder ID; сохранение через `SaveMessageDataCreator` и `MessageStore.saveRemoteMessage` |

Для ручных операций начинать с контроллера, для сетевого диалога с `mail/protocols`, для неверного состояния локального письма с backend sync и storage. По одному наличию `backend/jmap` нельзя заключать, что JMAP доступен во всех вариантах приложения: здесь не прослежена вся регистрация `developmentBackends` по вариантам.

## 3. Поток входящего сообщения

```text
MailSyncWorker / вызов MessagingController
  -> актуализация списка папок и обработка pending-команд
  -> BackendManager -> ImapBackend.sync
  -> ImapSync -> ImapStore / RealImapFolder / RealImapConnection
  -> MIME Message + FULL/PARTIAL
  -> K9BackendFolder.saveMessage
  -> SaveMessageDataCreator -> MessageStore.saveRemoteMessage
  -> K9MessageStore -> SaveMessageOperations
  -> SQLite messages/message_parts + файлы крупных частей
  -> SyncListener -> ControllerSyncListener -> слушатели/уведомления
```

1. В `MessagingController.syncFolder` pending-команды обрабатываются перед синхронизацией. Ошибка этого шага не всегда отменяет попытку sync: контроллер сохраняет исключение и продолжает. Папки `localOnly` по этому пути не синхронизируются.
2. [ImapBackend.kt](../../backend/imap/src/main/java/com/fsck/k9/backend/imap/ImapBackend.kt) делегирует получение в [ImapSync.kt](../../backend/imap/src/main/java/com/fsck/k9/backend/imap/ImapSync.kt). При смене `UIDVALIDITY` sync очищает локальные сообщения папки и сбрасывает highest known UID. UID следует рассматривать в контексте папки и её UIDVALIDITY, а не как глобальный ID письма.
3. `ImapSync` учитывает visible limit, earliest poll date, expunge policy и `syncRemoteDeletions`; сравнивает локальные и серверные сообщения. Это ограниченная синхронизация кеша, не обещание скачать весь ящик.
4. Сначала загружаются flags/envelope. Малые письма скачиваются как BODY и сохраняются с `FULL`. Для больших запрашивается STRUCTURE: при наличии структуры загружаются текстовые части, вложения остаются для последующей загрузки; без структуры используется BODY_SANE. Оба пути сохраняются как `PARTIAL`.
5. [RealImapFolder.kt](../../mail/protocols/imap/src/main/java/com/fsck/k9/mail/store/imap/RealImapFolder.kt), метод `fetch`, переводит `FetchProfile` в `UID FETCH`: FLAGS, INTERNALDATE, RFC822.SIZE, BODYSTRUCTURE, BODY.PEEK. Сетевое соединение и TLS искать в [RealImapConnection.kt](../../mail/protocols/imap/src/main/java/com/fsck/k9/mail/store/imap/RealImapConnection.kt); разбор ответов в [ImapResponseParser.java](../../mail/protocols/imap/src/main/java/com/fsck/k9/mail/store/imap/ImapResponseParser.java).
6. [SaveMessageDataCreator.kt](../../legacy/core/src/main/java/com/fsck/k9/mailstore/SaveMessageDataCreator.kt) формирует даты, тему, preview, fulltext, число вложений и encryption type. Определение шифрования влияет на эти метаданные, но не запускает расшифровку письма.
7. `ImapSync` обновляет локальные флаги и сообщает прогресс/результат через listener. Некоторые исключения при сохранении малых сообщений перехватываются внутри callback и логируются: событие `syncFinished` нельзя трактовать как доказательство безошибочного сохранения каждого письма.

Дозагрузка и отдельные действия: [CommandDownloadMessage.kt](../../backend/imap/src/main/java/com/fsck/k9/backend/imap/CommandDownloadMessage.kt), [CommandFetchMessage.kt](../../backend/imap/src/main/java/com/fsck/k9/backend/imap/CommandFetchMessage.kt), [CommandUploadMessage.kt](../../backend/imap/src/main/java/com/fsck/k9/backend/imap/CommandUploadMessage.kt). Это точки дальнейшего чтения для полного письма, вложения и APPEND соответственно.

Push исследовать отдельно от polling: [ImapBackendPusher.kt](../../backend/imap/src/main/java/com/fsck/k9/backend/imap/ImapBackendPusher.kt), [ImapFolderPusher.kt](../../backend/imap/src/main/java/com/fsck/k9/backend/imap/ImapFolderPusher.kt), [RealImapFolderIdler.kt](../../mail/protocols/imap/src/main/java/com/fsck/k9/mail/store/imap/RealImapFolderIdler.kt), [controller/push](../../legacy/core/src/main/java/com/fsck/k9/controller/push/). Полный жизненный цикл push в этой карте не разобран.

### Отличия POP3, JMAP и demo

| Backend | Проверенное поведение и ограничения |
|---|---|
| [Pop3Backend.kt](../../backend/pop3/src/main/java/com/fsck/k9/backend/pop3/Pop3Backend.kt) | Флаги возможностей move/copy/upload/search-by-date/push выключены. Полная загрузка реализована, загрузка структуры и отдельный `downloadMessage` не реализованы. `deleteMessages` делегирует установке DELETED |
| [Pop3Sync.kt](../../backend/pop3/src/main/java/com/fsck/k9/backend/pop3/Pop3Sync.kt) | Получает локальную карту UID и серверный список с учётом visible limit. [Pop3Folder.java](../../mail/protocols/pop3/src/main/java/com/fsck/k9/mail/store/pop3/Pop3Folder.java) принимает только INBOX, открывает соединение и получает число писем через STAT. Диалог входа и команды искать в [Pop3Connection.java](../../mail/protocols/pop3/src/main/java/com/fsck/k9/mail/store/pop3/Pop3Connection.java) |
| [JmapBackend.kt](../../backend/jmap/src/main/java/com/fsck/k9/backend/jmap/JmapBackend.kt) | Есть sync, upload, move/copy, delete и flags. `sendMessage`, search, fetchPart, download и pusher явно выбрасывают `UnsupportedOperationException`; не обещать функциональный паритет с IMAP |
| [JMAP CommandSync.kt](../../backend/jmap/src/main/java/com/fsck/k9/backend/jmap/CommandSync.kt) | Выбирает full/delta по query state. При `cannotCalculateChanges` сбрасывает state и возвращается к full sync; в построении query остаётся FIXME о сортировке |
| [Demo CommandSendMessage.kt](../../backend/demo/src/main/kotlin/app/k9mail/backend/demo/CommandSendMessage.kt) | Копирует MIME через сериализацию/парсинг и сохраняет во входящие с новым server ID. Это симуляция отправки, не проверка SMTP-доставки |

## 4. Поток исходящего сообщения

```text
MessageCompose -> SimpleMessageBuilder или PgpMessageBuilder
  -> onMessageBuildSuccess -> SendMessageTask
  -> MessagingController.sendMessage
  -> локальный Outbox + начальное OutboxState
  -> sendPendingMessagesSynchronous
  -> Backend.sendMessage -> SmtpTransport.sendMessage (IMAP/POP3)
  -> локальное перемещение в Sent или удаление
  -> PendingAppend для удалённой Sent, если применимо
```

- [MessageCompose.java](../../legacy/ui/legacy/src/main/java/com/fsck/k9/activity/MessageCompose.java) выбирает builder по crypto status и обрабатывает callback сборки. Черновик идёт через `SaveMessageTask`, обычное письмо через `SendMessageTask`.
- [SimpleMessageBuilder.java](../../legacy/core/src/main/java/com/fsck/k9/message/SimpleMessageBuilder.java) вызывает общую сборку `build()` и callback; MIME-сборку дальше искать в [MessageBuilder.java](../../legacy/core/src/main/java/com/fsck/k9/message/MessageBuilder.java). [MimeMessage.java](../../mail/common/src/main/java/com/fsck/k9/mail/internet/MimeMessage.java) содержит `parse`/`writeTo` для сетевого и локального представления.
- `MessagingController.sendMessage` сохраняет локальное письмо в Outbox, инициализирует состояние отправки, ставит фоновую команду. `sendMessageBlocking` является отдельным прямым вызовом backend и не проходит этот Outbox-путь.
- `sendPendingMessagesSynchronous` выбирает состояние `READY`, загружает envelope/body, пропускает черновики, увеличивает число попыток, выставляет `X_SEND_IN_PROGRESS`, вызывает backend. Ошибки авторизации/сертификата и остальные ошибки имеют разные ветки; смотреть также [OutboxStateRepository.kt](../../legacy/core/src/main/java/com/fsck/k9/mailstore/OutboxStateRepository.kt).
- [SmtpTransport.kt](../../mail/protocols/smtp/src/main/java/com/fsck/k9/mail/transport/smtp/SmtpTransport.kt) собирает адресатов TO/CC/BCC, затем удаляет заголовок Bcc из сообщения. Нет адресатов: ранний выход. Далее соединение, MAIL FROM, RCPT TO, DATA, MIME с преобразованием концов строк и dot-stuffing, завершающая точка. Есть ветка PIPELINING.
- После попытки завершающей точки SMTP-код считает, что письмо уже могло уйти (`entireMessageSent`): сбой на этой стадии нельзя автоматически считать доказательством недоставки. Этот путь не даёт гарантии exactly-once или конечной доставки в ящик адресата.
- `moveOrDeleteSentMessage` зависит от настроек Sent и uploadSentMessages. Если копия нужна, письмо перемещается локально, для удалённой Sent создаётся `PendingAppend`. SMTP-успех и успешный APPEND копии являются разными результатами. Типы pending-команд: [MessagingControllerCommands.java](../../legacy/core/src/main/java/com/fsck/k9/controller/MessagingControllerCommands.java).

## 5. Устройство локального хранения

| Слой | Ключевые файлы и ответственность |
|---|---|
| Контракт | [MessageStore.kt](../../legacy/mailstore/src/main/java/app/k9mail/legacy/mailstore/MessageStore.kt), [MessageStoreManager.kt](../../legacy/mailstore/src/main/java/app/k9mail/legacy/mailstore/MessageStoreManager.kt): операции и получение хранилища аккаунта |
| Создание реализации | [K9MessageStoreFactory.kt](../../legacy/storage/src/main/java/com/fsck/k9/storage/messages/K9MessageStoreFactory.kt): использует БД LocalStore, создаёт K9MessageStore, оборачивает в NotifierMessageStore и ListenableMessageStore |
| Делегирование операций | [K9MessageStore.kt](../../legacy/storage/src/main/java/com/fsck/k9/storage/messages/K9MessageStore.kt): искать конкретный `*Operations` по методу контракта |
| Совместимость со старым кодом | [LocalStore.java](../../legacy/core/src/main/java/com/fsck/k9/mailstore/LocalStore.java), [LocalFolder.java](../../legacy/core/src/main/java/com/fsck/k9/mailstore/LocalFolder.java), [LockableDatabase.java](../../legacy/core/src/main/java/com/fsck/k9/mailstore/LockableDatabase.java): legacy-чтение/папки, соединение с БД и блокировки |
| Сохранение | [SaveMessageOperations.kt](../../legacy/storage/src/main/java/com/fsck/k9/storage/messages/SaveMessageOperations.kt): insert/replace, дерево MIME-частей, threading, метаданные, fulltext |
| Файлы | [AndroidStorageFilesProvider.kt](../../legacy/core/src/main/java/com/fsck/k9/mailstore/AndroidStorageFilesProvider.kt): `Context.getDatabasePath("$accountId.db")` и `"$accountId.db_att"`; [AttachmentFileManager.kt](../../legacy/storage/src/main/java/com/fsck/k9/storage/messages/AttachmentFileManager.kt): файл по ID части, перенос временного файла, copy/delete |
| Чтение и изменения | [RetrieveMessageOperations.kt](../../legacy/storage/src/main/java/com/fsck/k9/storage/messages/RetrieveMessageOperations.kt), [MoveMessageOperations.kt](../../legacy/storage/src/main/java/com/fsck/k9/storage/messages/MoveMessageOperations.kt), [DeleteMessageOperations.kt](../../legacy/storage/src/main/java/com/fsck/k9/storage/messages/DeleteMessageOperations.kt), [ThreadMessageOperations.kt](../../legacy/storage/src/main/java/com/fsck/k9/storage/messages/ThreadMessageOperations.kt) |
| Схема и upgrades | [StoreSchemaDefinition.java](../../legacy/storage/src/main/java/com/fsck/k9/storage/StoreSchemaDefinition.java), [Migrations.kt](../../legacy/storage/src/main/java/com/fsck/k9/storage/migrations/Migrations.kt), [storage/migrations](../../legacy/storage/src/main/java/com/fsck/k9/storage/migrations/) |

Проверенные детали сохранения:

- Удалённое письмо определяется парой folder ID + server ID; локальному генерируется UID из локального префикса и UUID. Локальный database ID, server UID и заголовок Message-ID не взаимозаменяемы.
- MIME-дерево записывается в `message_parts` с root/parent/seq. Пустая пока часть получает `DataLocation.MISSING`; multipart ссылается на дочерние части.
- Порог `MAX_BODY_SIZE_FOR_DATABASE` равен 16 * 1024 байт. Размер тела сравнивается через `SizeAware`: больше порога означает `ON_DISK`, иначе `IN_DATABASE`. Это относится к телам MIME-частей, а не только к пользовательским вложениям.
- В `messages` отдельно сохраняются тема, адреса, даты, флаги, preview и encryption type. Fulltext создаётся из `SaveMessageData.textForSearchIndex`, если он не null. Наличие поля `encryption_type` не означает шифрование остальных столбцов.
- Операции выполняются через `LockableDatabase.execute(true)`, но работа с файлами существует отдельно от SQLite. Атомарность БД и файлов при аварийном завершении в этой задаче не доказана.
- Текущий `DB_VERSION` в прочитанном коде равен 91. [MigrationTo91.kt](../../legacy/storage/src/main/java/com/fsck/k9/storage/migrations/MigrationTo91.kt) добавляет/заполняет `account_id` в folders/messages и создаёт индексы; номер 89 в последовательности был пропущен. Не добавлять миграцию, исходя только из предположения о непрерывной нумерации.
- `StoreSchemaDefinition` запрещает downgrade, для версий ниже 61 пересоздаёт схему; в non-debug обработке ошибки upgrade присутствует сброс версии к 0 и повторный upgrade. Поэтому эта карта не обещает безусловную сохранность данных при миграциях.

## 6. Криптография, TLS и отображение

### Три разные задачи

| Задача | Реализация | Что можно утверждать по прочитанному коду |
|---|---|---|
| Защита сетевого соединения | [DefaultTrustedSocketFactory.kt](../../legacy/core/src/main/java/com/fsck/k9/helper/DefaultTrustedSocketFactory.kt), [TrustManagerFactory.java](../../mail/common/src/main/java/com/fsck/k9/mail/ssl/TrustManagerFactory.java), `open()` в SMTP/IMAP | Есть TLS socket factory, trust manager и режимы connection security. Для SMTP `STARTTLS_REQUIRED` отсутствие capability приводит к ошибке. Полная проверка цепочки доверия и всех режимов не выполнена |
| Подпись/шифрование исходящего MIME | [PgpMessageBuilder.java](../../legacy/core/src/main/java/com/fsck/k9/message/PgpMessageBuilder.java) | Выбор sign/encrypt зависит от crypto status, ключа, draft и режима. Операции выполняются через внешний `OpenPgpApi`; успех, ошибка и запрос пользовательского действия обрабатываются отдельно |
| Расшифровка/проверка для чтения | [MessageCryptoHelper.java](../../legacy/ui/legacy/src/main/java/com/fsck/k9/ui/crypto/MessageCryptoHelper.java) | `ACTION_DECRYPT_VERIFY`, отдельные пути signed/encrypted/inline, асинхронный результат и `CryptoResultAnnotation`. Результат decrypt и результат signature берутся отдельно |

Особенности, которые важно сохранить в понимании:

1. `PgpMessageBuilder` при отсутствии ключа возвращает построенное письмо без запуска криптографии. Когда ни подпись, ни шифрование не выбраны, также есть обычный путь завершения. Нельзя утверждать «все письма шифруются».
2. Черновики имеют отдельную политику `isEncryptAllDrafts` и encrypt-to-self. PGP/INLINE запрещает нетекстовое содержимое с вложениями. Перенос темы в зашифрованный payload зависит от `isEncryptSubject` и выбранного режима.
3. `MessageCompose` передаёт builder subject как `plaintextSubject` в сохранение, а `SaveMessageDataCreator` использует переданную тему вместо `message.subject`. Даже зашифрованный payload не доказывает отсутствие открытой темы в локальных метаданных.
4. [EncryptionDetector.java](../../legacy/crypto-openpgp/src/main/java/com/fsck/k9/crypto/openpgp/EncryptionDetector.java) ищет `multipart/encrypted`, `application/pkcs7-mime` и inline PGP-признаки. В самом файле есть FIXME о выделении S/MIME. Это структурная классификация, не криптографическая проверка и не доказательство поддержки расшифровки S/MIME.
5. [OpenPgpEncryptionExtractor.kt](../../legacy/crypto-openpgp/src/main/java/com/fsck/k9/crypto/openpgp/OpenPgpEncryptionExtractor.kt) выдаёт тип `openpgp` и attachment count 0 для обнаруженной структуры. [EncryptionExtractor.kt](../../legacy/core/src/main/java/com/fsck/k9/crypto/EncryptionExtractor.kt) задаёт encrypted preview и null fulltext по умолчанию. Не принимать этот label за проверенный алгоритм или достоверность подписи.
6. SQL- и файловый путь сохранения не содержит в прочитанных участках универсального шифрования хранилища. Защита устройства, настройки ОС, временные файлы, кеш расшифрованных данных и внешний OpenPGP-провайдер не аудированы. TLS не заменяет сквозное шифрование MIME.

Для Autocrypt продолжать в [legacy/core/autocrypt](../../legacy/core/src/main/java/com/fsck/k9/autocrypt/); в builder видны добавление заголовка и запрос материала ключа, в helper видны peer/gossip-обновления. Поведение внешнего провайдера за границей этой карты.

HTML-показ: [HtmlProcessor.kt](../../library/html-cleaner/src/main/kotlin/app/k9mail/html/cleaner/HtmlProcessor.kt) вызывает [HtmlSanitizer.kt](../../library/html-cleaner/src/main/kotlin/app/k9mail/html/cleaner/HtmlSanitizer.kt), затем добавляет head/classes. Для правил разрешённого HTML читать [BodyCleaner.kt](../../library/html-cleaner/src/main/kotlin/app/k9mail/html/cleaner/BodyCleaner.kt) и [HeadCleaner.kt](../../library/html-cleaner/src/main/kotlin/app/k9mail/html/cleaner/HeadCleaner.kt). Наличие sanitizer не является доказательством отсутствия всех атак через HTML или внешние ресурсы.

## 7. Реальные тесты и их границы

Тесты ниже найдены на диске. «Прочитан сценарий» означает чтение тела и assertions, а не запуск. Остальные строки служат точками навигации.

| Тест | Что искать / что прочитано |
|---|---|
| [ImapSyncTest.kt](../../backend/imap/src/test/java/com/fsck/k9/backend/imap/ImapSyncTest.kt) | Прочитаны сценарии пустой папки, отрицательного messageCount, закрытия папки при ошибке, expunge policy, remote deletions, earliest date и новых сообщений; fake backend storage и test IMAP folder |
| [RealImapFolderTest.kt](../../mail/protocols/imap/src/test/java/com/fsck/k9/mail/store/imap/RealImapFolderTest.kt), [RealImapConnectionTest.kt](../../mail/protocols/imap/src/test/java/com/fsck/k9/mail/store/imap/RealImapConnectionTest.kt) | Найдены: продолжение для FETCH/команд и соединения. Эти большие файлы здесь не прочитаны полностью |
| [Pop3FolderTest.kt](../../mail/protocols/pop3/src/test/java/com/fsck/k9/mail/store/pop3/Pop3FolderTest.kt) | Прочитаны open: только Inbox, сбой соединения/аутентификации, STAT и повторное открытие |
| [SmtpTransportTest.kt](../../mail/protocols/smtp/src/test/java/com/fsck/k9/mail/transport/smtp/SmtpTransportTest.kt) | Прочитаны STARTTLS, отсутствие capability, отсутствие адресатов, отправка одному адресату и ошибки PIPELINING/RCPT; используется MockSmtpServer |
| [MessagingControllerTest.java](../../legacy/core/src/test/java/com/fsck/k9/controller/MessagingControllerTest.java) | Прочитаны Outbox send, вызов backend, порядок флага in-progress, seen/progress, уведомления об auth/certificate failure; backend подменён mock |
| [SaveMessageOperationsTest.kt](../../legacy/storage/src/test/java/com/fsck/k9/storage/messages/SaveMessageOperationsTest.kt) | Прочитан тест text/plain: реальные assertions полей messages/message_parts/threads; начало сценария multipart. Robolectric SQLite и временная директория |
| [MigrationTo91Test.kt](../../legacy/storage/src/test/java/com/fsck/k9/storage/migrations/MigrationTo91Test.kt) | Прочитан полностью: account_id и повторное выполнение миграции на тестовой схеме. Не проверка обновления всех исторических версий приложения |
| [EncryptionDetectorTest.java](../../legacy/crypto-openpgp/src/test/java/com/fsck/k9/crypto/openpgp/EncryptionDetectorTest.java) | Прочитан полностью: MIME/inline/S-MIME классификация, отрицательные случаи preamble/quoted PGP; не выполняет реальное шифрование |
| [PgpMessageBuilderTest.kt](../../legacy/ui/legacy/src/test/java/com/fsck/k9/message/PgpMessageBuilderTest.kt) | Прочитаны настройка mock OpenPgpApi, ошибки состояний провайдера и начало cleartext-сценария. Полный файл и реальная работа внешнего провайдера не проверены |
| [MessageCryptoHelperTest.java](../../legacy/ui/legacy/src/test/java/com/fsck/k9/ui/crypto/MessageCryptoHelperTest.java) | Найден: искать callback/result/cancel и обработку частей для отображения |
| [CommandSyncTest.kt](../../backend/jmap/src/test/java/com/fsck/k9/backend/jmap/CommandSyncTest.kt) | Найден: JMAP sync; ответы и письма в [jmap_responses](../../backend/jmap/src/test/resources/jmap_responses/) |
| [HtmlSanitizerTest.kt](../../library/html-cleaner/src/test/kotlin/app/k9mail/html/cleaner/HtmlSanitizerTest.kt) | Найден: регрессии HTML-очистки |

Отдельного `backend/pop3/src/test` в обследованном дереве нет; существующие POP3-тесты находятся в `mail/protocols/pop3/src/test`. Не создавать ссылку на несуществующий каталог и не считать protocol-тесты полным покрытием backend sync.

## 8. Поиск для конкретной задачи

Все команды только читают исходники. Для буквального символа удобен `rg -n -F`; для связанных альтернатив используется регулярное выражение.

| Задача | Начало поиска из корня |
|---|---|
| Письмо не появилось / исчезло после sync | `rg -n 'UID_VALIDITY|syncRemoteDeletions|visibleLimit|downloadSmallMessages|downloadLargeMessages' backend/imap` |
| Не обновляются флаги | `rg -n 'syncFlags|setMessageFlag|CommandSetFlag' backend legacy/storage` |
| Вложение осталось пустым | `rg -n 'fetchPart|downloadCompleteMessage|DataLocation.MISSING' backend mail legacy` |
| Ошибка IMAP-диалога | `rg -n 'UID FETCH|BODY.PEEK|BODYSTRUCTURE|STARTTLS' mail/protocols/imap` |
| POP3 UID или скачивание | `rg -n 'UIDL|RETR|TOP|STAT|uidToMsg' mail/protocols/pop3 backend/pop3` |
| Outbox застрял / повторная отправка | `rg -n 'SendState.READY|MAX_SEND_ATTEMPTS|incrementSendAttempts|entireMessageSent' legacy/core mail/protocols/smtp` |
| Нет копии в Sent | `rg -n 'moveOrDeleteSentMessage|PendingAppend|uploadMessage' legacy/core backend` |
| Утеряна часть или неверный путь файла | `rg -n 'MAX_BODY_SIZE_FOR_DATABASE|moveTemporaryFile|getAttachmentDirectory|data_location' legacy` |
| Изменение схемы | `rg -n 'DB_VERSION|upgradeDatabase|MigrationTo91|account_id' legacy/storage` |
| Неверная тема/preview/поиск | `rg -n 'plaintextSubject|displaySubject|textForSearchIndex|messages_fulltext' legacy` |
| Шифрование и состояния провайдера | `rg -n 'ACTION_SIGN_AND_ENCRYPT|ACTION_DECRYPT_VERIFY|RESULT_CODE_USER_INTERACTION_REQUIRED' legacy` |
| Ошибка TLS / сертификата | `rg -n 'STARTTLS_REQUIRED|CertificateValidationException|getTrustManagerForDomain' mail legacy/core` |
| Найти тест по классу | `rg --files backend mail legacy library -g '*Test*'` |
| HTML или автодополнение | `rg -n 'sanitize|processForDisplay' library/html-cleaner`; `rg --files library/token-auto-complete` |

Пять практических правил для изменений:

1. Сначала определить слой сбоя и воспроизвести его на подходящем тесте. Иначе сетевой дефект легко маскируется правкой UI; трудность в разграничении server ID и local ID.
2. При работе с sync учитывать частичную загрузку и лимиты. Иначе незагруженное вложение можно ошибочно принять за потерю данных; нужны случаи FULL/PARTIAL/MISSING.
3. При изменении отправки проверять Outbox и Sent отдельно. Иначе повторная попытка может скрыть уже состоявшуюся отправку; сложнее всего сбой после DATA.
4. При изменении хранения проверять БД и файлы частей вместе. Иначе остаются сиротские файлы или отсутствующие тела; тесты должны учитывать аварийные ветки и миграции.
5. При изменении crypto разделять MIME, metadata, TLS и внешний провайдер. Иначе интерфейс или документация обещает лишнюю защиту; проверки с mock не заменяют сценарий с реальным провайдером.

## 9. Что прочитано и что осталось за границей

Инструкции: прочитаны `CLAUDE.md`, `AGENTS.md`, содержимое 19 исходных файлов памяти в `.memory-bank/` (большие выводы перечитывались частями), `README.md`, `docs/CONTRIBUTING.md`. Общие карты и журналы не менялись. Чтение памяти не означает проверку актуальности всех её исторических результатов.

Полностью прочитаны ключевые реализации: `Backend.kt`, `ImapBackend.kt`, `Pop3Backend.kt`, `JmapBackend.kt`, `ImapSync.kt` (двумя частями), `BackendManager.kt`, `backends/KoinModule.kt`, `ImapBackendFactory.kt`, `K9BackendFolder.kt`, `SaveMessageDataCreator.kt`, `K9MessageStoreFactory.kt`, `AttachmentFileManager.kt`, `AndroidStorageFilesProvider.kt`, `EncryptionExtractor.kt`, `EncryptionDetector.java`, `OpenPgpEncryptionExtractor.kt`, `MailSyncWorker.kt`, `SimpleMessageBuilder.java`, demo `CommandSendMessage.kt`, `Migrations.kt`, `MigrationTo91.kt`, `HtmlProcessor.kt`, `HtmlSanitizer.kt`. Полностью прочитанные тесты: `EncryptionDetectorTest.java`, `MigrationTo91Test.kt`.

Выборочно прочитаны: `MessagingController.java` (syncFolder, постановка/исполнение отправки, Sent); `SaveMessageOperations.kt` (сохранение/замена, MIME-части, размер, запись метаданных/fulltext); `SmtpTransport.kt` (TLS-open и sendMessage); `PgpMessageBuilder.java` (выбор режима, API, обработка результата); `MessageCryptoHelper.java` (decrypt/verify и результат); `MessageCompose.java` (выбор builder, callback и передача отправки); `RealImapFolder.kt` (FETCH); `Pop3Folder.java` (open/close); `Pop3Sync.kt` и JMAP `CommandSync.kt` (начальные стадии); `StoreSchemaDefinition.java` (версия и upgrade); `DefaultTrustedSocketFactory.kt` (создание сокета и начало настройки). Степень чтения тестов приведена в таблице выше.

Остальные ссылки проверяются на существование и служат точками следующего чтения. В частности, полностью не изучены MIME parser, LocalStore/LockableDatabase, все миграции, настройки доверия TLS, IDLE/push, все источники запуска sync, OAuth-refresh и HTML allowlist. Карта не является полным чтением `backend`, `mail`, `legacy`, `library` и не подтверждает безопасность внешнего OpenPGP-провайдера.

## 10. Проверка этой карты

Проверять ссылки относительно `.memory-bank/code-map`, включая каталоги, через `Test-Path -LiteralPath`; все ссылки на код имеют префикс `../../`. Проверка структуры должна также контролировать непустоту, отсутствие пробелов в конце строк и завершающий перевод строки. `git diff --check` отдельно не охватывает новый untracked-файл.

Gradle, Android-тесты, mdbook, сборки и публикации не запускались по границам этой задачи. Регистрация страницы в общих картах остаётся у ответственного за общие документы: здесь разрешён только `mail-and-storage.md`.
