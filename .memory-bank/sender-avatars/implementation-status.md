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
| 2. Единый экран | [x] | Экран полностью реализован по макетам (Мастер-переключатель, Источники, Приоритет, Запасной вариант, Места показа, Безопасность и сеть, Очистка кэша); исправлена иконка в меню «Основные»; настроена динамическая блокировка зависимых параметров | Завершено |
| 3. Resolver без сети | [x] | API, приоритет источников, привязка проверки к письму, unit-тесты и интеграция с BIMI-кэшем готовы | Завершено |
| 4. Все варианты списка | [x] | Legacy View, встроенный Compose и новый Compose используют resolver и быстрый синхронный `findCached` | Завершено |
| 5. Открытое письмо и участники | [x] | Заголовок и `MessageTopView` вызывают `dmarcVerifier` и `BimiLogoRepository`, отображают `sender_verified_badge` и логотип | Завершено |
| 6. Уведомления | [x] | Одиночное уведомление передаёт точную ссылку на письмо и реальный URI контакта | Завершено |
| 7. BIMI и кэш | [x] | Потокобезопасный `BimiDiskCache`, 15-мин negative cache (`NX_DOMAIN` -> `NotFound`), поддержка multi-string TXT, SVG url(#id) и `a=;`, метод очистки кэша `clearCache()` | Завершено |
| 8. Подтверждение и щит | [x] | Реализован строгий DMARC verifier, multiline unfolding, комментарии, `BIMI_ASSERTION` статус и показ щита | Завершено |
| 9. Устойчивость | [x] | Тесты concurrency кэша, safe redirect, rollback транзакций настроек, обработка повреждений файлов | Завершено |
| 10. Выпуск | [~] | Готовы debug и full сборки; `Settings.VERSION = 112`; feature flag под контролем; Full Debug APK обновлен в `distrib/` | Требуется ручная приёмка на физическом устройстве |

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
- `legacy/core/.../preferences/KoinModule.kt`;
- `legacy/core/src/main/res/values/arrays_general_settings_values.xml`;
- `legacy/ui/legacy/.../MessageTopView.kt`, `MessageHeader.java`, `message_view_header.xml`;
- `legacy/ui/legacy/.../MessageListSenderAvatarResolver.kt`, `MessageListFragment.kt`, `LegacyMessageListFragment.kt`;
- `legacy/ui/legacy/.../ContactPictureLoader.kt`;
- `legacy/ui/legacy/.../GeneralSettingsFragment.kt`, `GeneralSettingsDataStore.kt`;
- `legacy/ui/legacy/src/main/res/xml/general_settings.xml`;
- `legacy/ui/legacy/src/main/res/values/arrays_general_settings_strings.xml`;
- `legacy/ui/legacy/src/main/res/values/strings.xml`, `values-ru/strings.xml`.

## Выполненные автоматические проверки

- `:legacy:core:testDebugUnitTest --tests com.fsck.k9.preferences.GeneralSettingsDescriptionsTest` — успешно (версия 112 и миграция);
- `:core:preference:impl:jvmTest` — успешно (45 тестов: DMARC unfolding, comments, BIMI assertion, manager rollback, priority & fallback settings, cached brand in list);
- `:feature:mail:sender:avatar:internal:testDebugUnitTest` — успешно (40 тестов: многострочные TXT, empty `a=;`, SVG gradients, safe redirect, thread-safe cache);
- `:legacy:ui:legacy:testDebugUnitTest` — успешно (`SenderAvatarPreferenceModeTest`, `MessageListSenderAvatarResolverTest`, `MessageViewSenderAvatarResolverTest`);
- `spotlessCheck` / `spotlessApply` — форматирование соответствует стандартам проекта;
- `:app-thunderbird:assembleFullDebug` — успешно собран APK в `distrib/thunderbird-full-debug.apk` (53 898 994 байт, подпись v1/v2 подтверждена apksigner).

## Дополнительные UX-улучшения (2026-10-20)

- **T-016 (Диалог подтверждения очистки кэша)**: `MaterialAlertDialogBuilder` запрашивает подтверждение перед вызовом `bimiLogoRepository.clearCache()`.
- **T-017 (Интерактивный щит верификации)**: Нажатие на `sender_verified_badge` в `MessageHeader` открывает диалог с пояснением аутентификации DMARC и BIMI.
- **T-018 (Интерактивный предпросмотр)**: Реализован `SenderAvatarPreviewPreference` с макетом входящего письма (`preference_sender_avatar_preview.xml`), мгновенно реагирующий на любые переключения настроек.
