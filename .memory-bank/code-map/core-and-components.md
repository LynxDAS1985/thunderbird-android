# Общая инфраструктура и UI-компоненты

Область: `core/`, `components/`, `ui-utils/`. Перечень ниже охватывает непосредственные группы; полный список файлов получать через `rg --files core components ui-utils`.

## Группы core

| Папка | Назначение и точка входа |
|---|---|
| [architecture](../../core/architecture) | Общие архитектурные типы, в частности идентификаторы; API в `api/src/commonMain` |
| [common](../../core/common) | Общие контракты ресурсов, бренда, версии, OAuth и конфигурации платформы |
| [configstore](../../core/configstore) | API хранения настроек, backend, преобразования и миграции; см. BaseConfigStore ниже |
| [preference](../../core/preference) | Типизированные пользовательские настройки: display, notification, network, privacy, interaction, debugging |
| [featureflag](../../core/featureflag) | Каталоги флагов и выбор доступности функций, runtime-переопределения для отладки |
| [file](../../core/file) | Общий API файловых действий и реализации Android/JVM |
| [logging](../../core/logging) | API логирования, console/file/composite реализации, конфигурация и тестовая поддержка |
| [mail](../../core/mail) | Общие сведения о почтовых серверах; это не реализации IMAP/SMTP |
| [outcome](../../core/outcome) | Представление результата операции и ошибок |
| [testing](../../core/testing) | Общие помощники тестирования |
| [validation](../../core/validation) | Валидация входных значений и поля ввода |
| [android](../../core/android) | Системные интеграции Android, см. следующую таблицу |
| [ui](../../core/ui) | UI-контракты, темы, навигация, настройки и совместимость со старыми экранами |

| Android-группа | Что искать |
|---|---|
| [account](../../core/android/account) | LegacyAccountManager, преобразования аккаунтов и значения по умолчанию |
| [common](../../core/android/common) | Android-ресурсы, Activity, контакты, камера, общие привязки Koin |
| [contact](../../core/android/contact) | Работа с контактами Android |
| [logging](../../core/android/logging) | Android-реализации логирования |
| [network](../../core/android/network) | Наблюдение доступности сети, ConnectivityManager |
| [permissions](../../core/android/permissions) | Общие Android-разрешения и DI |
| [testing](../../core/android/testing) | Помощники Android-тестов |
| [webkit](../../core/android/webkit) | API и внутренняя реализация взаимодействия с WebKit |

| UI-группа | Что искать |
|---|---|
| [account](../../core/ui/account) | Общие визуальные элементы аккаунта |
| [animation](../../core/ui/animation) | Управление анимациями |
| [compose](../../core/ui/compose) | Общий код Compose и поддержка его тестов |
| [contract](../../core/ui/contract) | MVI/UDF: состояние, события, эффекты и базовые классы |
| [legacy](../../core/ui/legacy) | Старая дизайн-система и темы обоих брендов |
| [navigation](../../core/ui/navigation) | Общие контракты навигации |
| [setting](../../core/ui/setting) | API настроек, компоненты и диалоговая реализация |
| [theme](../../core/ui/theme) | API тем и ThemeManager |

## Прослеженные реализации

- [BaseViewModel.kt](../../core/ui/contract/src/commonMain/kotlin/net/thunderbird/core/ui/contract/mvi/BaseViewModel.kt) хранит состояние в StateFlow, эффекты в SharedFlow, меняет состояние через `updateState`, посылает эффект через `emitEffect`; `handleOneTimeEvent` запоминает обработанные события. Тесты: [BaseViewModelTest.kt](../../core/ui/contract/src/commonTest/kotlin/net/thunderbird/core/ui/contract/mvi/BaseViewModelTest.kt).
- [BaseConfigStore.kt](../../core/configstore/api/src/commonMain/kotlin/net/thunderbird/core/configstore/BaseConfigStore.kt) получает backend через provider, читает набор ключей, преобразует в конфигурацию, запускает проверку миграции при начале чтения потока. Обновление проходит через backend.update. Тесты: [BaseConfigStoreTest.kt](../../core/configstore/api/src/commonTest/kotlin/net/thunderbird/core/configstore/BaseConfigStoreTest.kt), [миграции](../../core/configstore/api/src/commonTest/kotlin/net/thunderbird/core/configstore/BaseConfigStoreMigrationTest.kt).
- [GeneralSettingsManager.kt](../../core/preference/api/src/commonMain/kotlin/net/thunderbird/core/preference/GeneralSettingsManager.kt) расширяет PreferenceManager; старые `getSettings` и `getSettingsFlow` помечены deprecated в пользу `getConfig`/`getConfigFlow`. При новой работе не копировать устаревшие вызовы только потому, что они есть у соседей.
- [ThemeManager.kt](../../core/ui/theme/manager/src/main/java/net/thunderbird/core/ui/theme/manager/ThemeManager.kt) читает общую тему и подтемы чтения/написания письма, наблюдает настройки в `init`, меняет режим AppCompatDelegate. Ресурсы передаёт [ThemeProvider.kt](../../core/ui/theme/api/src/commonMain/kotlin/net/thunderbird/core/ui/theme/api/ThemeProvider.kt).
- [RuntimeDebugOverrideFeatureFlagProvider.kt](../../core/featureflag/src/commonMain/kotlin/net/thunderbird/core/featureflag/provider/RuntimeDebugOverrideFeatureFlagProvider.kt) наблюдает сохранённые overrides, обновляет разрешённые флаги и позволяет менять/очищать debug-переопределения. Это не доказательство включённости всех новых экранов в release.
- [DefaultLogger.kt](../../core/logging/api/src/commonMain/kotlin/net/thunderbird/core/logging/DefaultLogger.kt) проверяет допустимость уровня у sink до вычисления сообщения, затем передаёт событие с временем. Это не автоматическое удаление персональных данных: не передавать их в Logger.
- [DefaultFileManager.kt](../../core/file/src/commonMain/kotlin/net/thunderbird/core/file/DefaultFileManager.kt) делегирует copy/delete/createDirectories соответствующим командам и возвращает Outcome. Реальная платформенная работа находится в Android/JVM реализациях.
- [ConnectivityManager.kt](../../core/android/network/src/main/kotlin/net/thunderbird/core/android/network/ConnectivityManager.kt) определяет start/stop и подписки на изменение сети; это интерфейс, детали исполнения искать через `: ConnectivityManager`.
- [BrandNameProvider.kt](../../core/common/src/commonMain/kotlin/net/thunderbird/core/common/provider/BrandNameProvider.kt) - контракт получения бренда. Замена имени начинается с реализации в приложении, а не с переименования интерфейса.

## Отдельная сборка components

[components/settings.gradle.kts](../../components/settings.gradle.kts) включает `:ui:bolt`, `:ui:catalog`, `:ui:testing`. Корневой settings управляет локальной подстановкой через `tb.components.local` и `tb.components.local.bolt`.

| Папка | Назначение |
|---|---|
| [ui/bolt](../../components/ui/bolt) | Compose дизайн-система: atom, molecule, organism, темы и общие размеры/цвета |
| [ui/catalog](../../components/ui/catalog) | Отдельное приложение-каталог UI-компонентов, не основной почтовый клиент |
| [ui/testing](../../components/ui/testing) | Поддержка тестов Compose на разных платформах |

README компонентов содержит концептуальные названия `apps-xyz`, `features`, `build-logic`; реальные папки этого снимка - `app-thunderbird`, `feature`, `build-plugin`. Для поиска верить дереву и settings, не трактовать примеры README как фактические пути.

## Старые View-утилиты

[item-touch-helper](../../ui-utils/item-touch-helper), [linear-layout-manager](../../ui-utils/linear-layout-manager), [toolbar-bottom-sheet](../../ui-utils/toolbar-bottom-sheet) обслуживают View-экраны. Прежде чем менять жесты или прокрутку, проверить конкретного потребителя: новый Compose-экран может не использовать эти классы.

## Уровень изучения

Указанные реализации и settings прочитаны целиком; таблицы групп построены по реальному дереву и настройкам модулей. Все 701 файла core, 400 файлов components и 16 файлов ui-utils построчно не аудитированы. Числа относятся к исходному Git-снимку до добавления нашей документации.
