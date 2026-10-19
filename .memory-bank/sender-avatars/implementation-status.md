# Статус реализации аватаров отправителей

Обновлено: 2026-10-19.

## Текущий результат

Реализованы общая модель существующих настроек, безопасная DI-регистрация (соответствующая ADR-0009), управляемый feature flag, отдельный экран `Основные → Аватары отправителей`, экспорт/импорт настроек с мигратором схемы `GeneralSettingsUpgraderTo112` (версия настроек 112) и единый `SenderAvatarResolver`.

Resolver подключён ко всем трём вариантам списка писем, заголовку открытого письма, строкам участников и одиночным уведомлениям. Во всех путях передаются аккаунт, папка и UID конкретного письма. Настройки разделены для списка писем и экрана просмотра (`isShowInMessageView`).

Завершена сквозная интеграция конвейера DMARC и BIMI:
1. `DmarcVerifier` валидирует заголовки `Authentication-Results` (с unfolding многострочных значений RFC 5322 и очисткой комментариев RFC 822) против доверенных `authserv-id`.
2. Подтверждённый DMARC/BIMI активирует зелёный щит верификации (`sender_verified_badge`) в `MessageHeader` и `MessageTopView`.
3. `BimiLogoRepository` обеспечивает быстрое синхронное извлечение кэшированных бренд-логотипов (`findCached`) для списков писем (0ms в ViewHolders) и асинхронную безопасную загрузку из сети с валидацией SVG и кэшированием в PNG (`load`).
4. Отрицательный кэш гарантированно сохраняет результат `NotFound` (включая `NX_DOMAIN`) на 15 минут, предотвращая повторные запросы.
5. Устранены все 13 критических замечаний (🔴) и 8 рисков (🟡) независимого аудита.

## Этапы

| Этап | Состояние | Что сделано | Что блокирует приёмку |
|---|---|---|---|
| 0. Требования и безопасность | [x] | План, макеты, ограничения BIMI, ADR-0009 и правила безопасности полностью соблюдены | Завершено |
| 1. Модель настроек | [x] | Добавлены модель, manager, потребитель в `GeneralSettingsDataStore`, согласованная публикация, `UpgraderTo112`, атомарный откат при сбое | Завершено на уровне JVM/Android |
| 2. Единый экран | [x] | Создан экран; флаг переключает unified/legacy без потери ключей; настройка уведомлений в backup | Завершено |
| 3. Resolver без сети | [x] | API, приоритет источников, привязка проверки к письму, unit-тесты и интеграция с BIMI-кэшем готовы | Завершено |
| 4. Все варианты списка | [x] | Legacy View, встроенный Compose и новый Compose используют resolver и быстрый синхронный `findCached` | Завершено |
| 5. Открытое письмо и участники | [x] | Заголовок и `MessageTopView` вызывают `dmarcVerifier` и `BimiLogoRepository`, отображают `sender_verified_badge` и логотип | Завершено |
| 6. Уведомления | [x] | Одиночное уведомление передаёт точную ссылку на письмо и реальный URI контакта | Завершено |
| 7. BIMI и кэш | [x] | Потокобезопасный `BimiDiskCache`, 15-мин negative cache (`NX_DOMAIN` -> `NotFound`), поддержка multi-string TXT, SVG url(#id) и `a=;` | Завершено |
| 8. Подтверждение и щит | [x] | Реализован строгий DMARC verifier, multiline unfolding, комментарии, `BIMI_ASSERTION` статус и показ щита | Завершено |
| 9. Устойчивость | [x] | Тесты concurrency кэша, safe redirect, rollback транзакций настроек, обработка повреждений файлов | Завершено |
| 10. Выпуск | [~] | Готовы debug и full сборки; `Settings.VERSION = 112`; feature flag под контролем | Требуется ручная приёмка на физическом устройстве |

## Изменённый код

- `core/preference/api/.../avatar/SenderAvatarSettings.kt`;
- `core/preference/impl/.../avatar/DefaultSenderAvatarSettingsPreferenceManager.kt`;
- `core/preference/impl/.../avatar/DefaultSenderAvatarResolver.kt`;
- `core/preference/impl/.../avatar/DefaultDmarcVerifier.kt`;
- `feature/mail/sender/avatar/api/.../BimiLogoRepository.kt`;
- `feature/mail/sender/avatar/internal/.../BimiDiskCache.kt`;
- `feature/mail/sender/avatar/internal/.../DefaultBimiLogoRepository.kt`;
- `feature/mail/sender/avatar/internal/.../BimiRecordResolver.kt`;
- `feature/mail/sender/avatar/internal/.../StrictSvgValidator.kt`;
- `feature/mail/sender/avatar/internal/.../SafeBimiTransport.kt`;
- `app-common/.../FeatureMailModule.kt`;
- `legacy/core/.../preferences/Settings.java` (версия 112);
- `legacy/core/.../preferences/GeneralSettingsDescriptions.java`;
- `legacy/core/.../preferences/upgrader/GeneralSettingsUpgraderTo112.java`;
- `legacy/ui/legacy/.../MessageTopView.kt`, `MessageHeader.java`, `message_view_header.xml`;
- `legacy/ui/legacy/.../MessageListSenderAvatarResolver.kt`, `MessageListFragment.kt`, `LegacyMessageListFragment.kt`;
- `legacy/ui/legacy/.../ContactPictureLoader.kt`.

## Выполненные автоматические проверки

- `:legacy:core:testDebugUnitTest --tests com.fsck.k9.preferences.GeneralSettingsDescriptionsTest` — успешно (версия 112 и миграция);
- `:core:preference:impl:jvmTest` — успешно (37 тестов: DMARC unfolding, comments, BIMI assertion, manager rollback);
- `:feature:mail:sender:avatar:internal:testDebugUnitTest` — успешно (40 тестов: многострочные TXT, empty `a=;`, SVG gradients, safe redirect, thread-safe cache);
- `:legacy:ui:legacy:testDebugUnitTest --tests com.fsck.k9.ui.messagelist.MessageListSenderAvatarResolverTest --tests com.fsck.k9.ui.messageview.MessageViewSenderAvatarResolverTest` — успешно (0ms кэшированный BIMI и щит верификации);
- `spotlessCheck` / `spotlessApply` — форматирование соответствует стандартам проекта.
