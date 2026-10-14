# Сборка, тесты, CI и документация

Карта рабочего дерева на 2026-10-09. Область: `build-plugin`, `plugins`, `gradle`, `quality`, `cli`, `.github`, `docs`, `config`, `schemas`, `scripts` и корневая конфигурация Gradle. Исходники не перемещаются. Команды ниже являются справочником по файлам проекта, а не отчётом об их успешном запуске.

## 1. Откуда начинается сборка

| Точка входа | Назначение и следующий шаг |
|---|---|
| [settings.gradle.kts](../../settings.gradle.kts) | Имя сборки `tfa`, type-safe project accessors, перечень `include(...)`, репозитории и included builds. Сначала искать модуль здесь, затем открывать его `build.gradle.kts` |
| [build.gradle.kts](../../build.gradle.kts) | Объявления plugin aliases, dependency updates, coverage, Spotless, корневой feature flag plugin, `buildCliTools`, параметры задачи `wrapper` |
| [gradlew](../../gradlew), [gradlew.bat](../../gradlew.bat), [gradle-wrapper.properties](../../gradle/wrapper/gradle-wrapper.properties) | Запуск через Wrapper. В текущем properties указан `gradle-9.7.0-all.zip` и SHA-256 дистрибутива |
| [libs.versions.toml](../../gradle/libs.versions.toml) | Разделы versions/plugins/libraries/bundles. Сейчас AGP `9.2.1`, Kotlin BOM `2.4.10`. Значение `gradle = "9.6.1"` расходится с Wrapper; корневая задача `wrapper` берёт версию и checksum именно отсюда |
| [.java-version](../../.java-version), [gradle-daemon-jvm.properties](../../gradle/gradle-daemon-jvm.properties) | Java 21; daemon criteria также задают 21 и URL toolchain. Корневые settings проверяют Java 21+ |
| [ThunderbirdProjectConfig.kt](../../build-plugin/src/main/kotlin/ThunderbirdProjectConfig.kt) | `minSdk=23`, `targetSdk=36`, `compileSdk=37`; bytecode Java/Kotlin 17. Это отличается от JDK запуска Gradle и JDK 21 для реализации build plugins |
| [gradle.properties](../../gradle.properties) | Parallel, build/configuration cache, isolated projects, in-process Kotlin compiler, JVM heap 10g. Не предполагать, что конфигурация совпадает с пользовательским Gradle home |

Поток конфигурации:

1. Wrapper выбирает дистрибутив; settings подключают `build-plugin` через `pluginManagement.includeBuild`.
2. Репозитории зависимостей централизованы: `FAIL_ON_PROJECT_REPOS`, Google с фильтрами групп, Maven Central, Mozilla Maven и JitPack с ограниченным набором групп. Репозитории плагинов заданы отдельно.
3. `tb.components.local` по умолчанию `true`; `tb.components.local.bolt` наследует его, если не задан отдельно. При включении любого флага подключается [components/settings.gradle.kts](../../components/settings.gradle.kts). Substitution для `net.thunderbird.components.ui.bolt:bolt` и `net.thunderbird.components.ui:testing` управляется соответствующими флагами.
4. `include(...)` регистрирует приложения и обычные модули. `build-plugin` и `components` являются отдельными сборками; `quality:e2e` не включён как Gradle-модуль, а `quality:konsist` включён.
5. Модуль применяет convention plugin, дополняет зависимости/варианты/source sets. После конфигурации Gradle строит граф задач. Проверка feature flag catalog может завершить конфигурацию ещё до компиляции.

Важно при поиске: имя каталога не всегда равно самостоятельной Gradle-сборке; наличие каталога тестов не означает регистрацию задачи на устройстве. Фактический граф задач в этой работе не запрашивался.

## 2. Два слоя build-plugin

Причина централизации описана в [ADR-0005](../../docs/engineering/adr/0005-central-project-configuration.md). [build-plugin/settings.gradle.kts](../../build-plugin/settings.gradle.kts) импортирует общий version catalog из `../gradle/libs.versions.toml` и включает `:plugin`.

| Слой | Файлы | Что менять/искать |
|---|---|---|
| Precompiled convention plugins | [build-plugin/build.gradle.kts](../../build-plugin/build.gradle.kts), [src/main/kotlin](../../build-plugin/src/main/kotlin), [ThunderbirdPlugins.kt](../../build-plugin/src/main/kotlin/ThunderbirdPlugins.kt) | Общие настройки типов модулей; зависимости на Gradle plugins; константы идентификаторов. Файл `thunderbird.*.gradle.kts` задаёт одноимённый plugin ID |
| Реализации `Plugin<Project>` | [plugin/build.gradle.kts](../../build-plugin/plugin/build.gradle.kts), [plugin/src/main/kotlin](../../build-plugin/plugin/src/main/kotlin) | Регистрация `net.thunderbird.gradle.plugin.*`, классы задач и extensions. Реализация компилируется для JVM 21; `validatePlugins` включает strict validation и `failOnWarning` |
| Общие helpers | [KotlinExtension.kt](../../build-plugin/src/main/kotlin/KotlinExtension.kt), [KotlinMultiplatformExtension.kt](../../build-plugin/src/main/kotlin/KotlinMultiplatformExtension.kt), [ProjectExtension.kt](../../build-plugin/src/main/kotlin/ProjectExtension.kt) | Java/Kotlin compatibility, accessor `androidHostTest`, зависимости KMP с excludes, доступ к каталогу `libs` |

### Convention plugins и source sets

| Plugin / реализация | Поведение, source sets и тесты |
|---|---|
| [thunderbird.app.android](../../build-plugin/src/main/kotlin/thunderbird.app.android.gradle.kts) | Android application, SDK из общего объекта, desugaring, `BuildConfig`, `AndroidJUnitRunner`, lint, test bundles. Обычная организация `src/main`, `src/test`, `src/androidTest`; варианты уточняет приложение |
| [thunderbird.app.android.compose](../../build-plugin/src/main/kotlin/thunderbird.app.android.compose.gradle.kts) | Дополняет предыдущий Compose compiler/BOM, UI/testing bundles, debug tooling и mobile components. Не заменяет настройки конкретного приложения |
| [thunderbird.library.android](../../build-plugin/src/main/kotlin/thunderbird.library.android.gradle.kts) | Android library, `BuildConfig=false`, SDK/lint/packaging, JVM arguments для Robolectric, общие test dependencies |
| [thunderbird.library.android.compose](../../build-plugin/src/main/kotlin/thunderbird.library.android.compose.gradle.kts) | Compose + serialization; через `beforeVariants(release)` отключает unit host tests и Android tests release-варианта |
| [thunderbird.library.jvm](../../build-plugin/src/main/kotlin/thunderbird.library.jvm.gradle.kts) | `java-library` и Kotlin JVM, target 17, bundles. Имена JAR берутся из полного project path: например, `core.common`. Тестовые JVM получают разрешения dynamic agent/self attach |
| [thunderbird.app.jvm](../../build-plugin/src/main/kotlin/thunderbird.app.jvm.gradle.kts) | `application` + Kotlin JVM. Основа CLI: `src/main/kotlin`, `application.mainClass`, `installDist`, тесты JVM |
| [thunderbird.library.kmp](../../build-plugin/src/main/kotlin/thunderbird.library.kmp.gradle.kts) | Android KMP library + JVM target + serialization; `commonMain/commonTest`, `androidMain/androidHostTest`, `jvmMain/jvmTest`. `withHostTest` включает Android resources. `androidHostTest` исполняется на хосте, не на устройстве |
| [thunderbird.library.kmp.compose](../../build-plugin/src/main/kotlin/thunderbird.library.kmp.compose.gradle.kts) | Самостоятельный convention script с Android/JVM targets, Compose resources, UI test bundles, `compose.desktop.currentOs` для JVM tests. Android resources включены; общие зависимости mobile components исключаются при `rootProject.name == "components"` |

В обоих KMP scripts нет настройки device test source set или iOS target. Это описание этих conventions, а не запрет модулю расширять конфигурацию. Каталоги `src/*/java` тоже встречаются в Kotlin-проекте: не ограничивать поиск одним `kotlin`.

Robolectric JVM options централизованы в [ThunderbirdProjectConfig.kt](../../build-plugin/src/main/kotlin/ThunderbirdProjectConfig.kt): `-XX:TieredStopAtLevel=3` и `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED`. Android conventions добавляют их unit tests; KMP conventions добавляют всем задачам `Test`.

### Что именно делает testsOnCi

Это одноимённые задачи в модулях, зарегистрированные conventions; в корневом `build.gradle.kts` отдельного списка всех тестов нет.

| Тип модуля | Зависимости `testsOnCi` |
|---|---|
| Android app/library и JVM app | Все задачи типа `Test`, кроме имени **строго** `testReleaseUnitTest` |
| JVM library | Задачи `Test`, в имени которых нет строки `konsist` |
| KMP и KMP Compose | Все задачи типа `Test` |
| `quality:konsist` | Применён напрямую Kotlin JVM, собственной `testsOnCi` нет; CI вызывает `:quality:konsist:test` отдельно |

Следствие буквального сравнения: исключение `testReleaseUnitTest` само по себе не исключает `testFossReleaseUnitTest` или `testFullReleaseUnitTest`. Не описывать этот набор как «все тесты, кроме release». Он также не означает Maestro, connected device tests или тесты included build `build-plugin`.

### Варианты, подпись и метаданные артефактов

[app-thunderbird/build.gradle.kts](../../app-thunderbird/build.gradle.kts) задаёт flavors `foss/full` в dimension `app` и build types `debug/release/beta/daily`; [app-k9mail/build.gradle.kts](../../app-k9mail/build.gradle.kts) задаёт `foss/full` и `debug/release`. `assembleDebug` агрегирует debug-варианты, а не выбирает один flavor. Конкретный пример выбора: `:app-thunderbird:assembleFossDebug`.

`-Pci=true` меняет настройки оптимизации: у Thunderbird release использует `!isCI`, а beta/daily используют `isCI` для minify/shrink. Поэтому CI assemble нельзя автоматически считать эквивалентом локальной release-сборки.

- [SigningExtensions.kt](../../build-plugin/src/main/kotlin/SigningExtensions.kt) читает `.signing/<id>.upload.properties` либо `.signing/<id>.signing.properties`; [SigningType.kt](../../build-plugin/src/main/kotlin/SigningType.kt) задаёт `k9.release`, `tb.release`, `tb.beta`, `tb.daily`. При отсутствии полного набора свойств конфигурация подписи не создаётся и выводится предупреждение. Наличие подписанного APK этим не доказывается.
- [BadgingPlugin.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/app/badging/BadgingPlugin.kt) регистрирует `generate/check/update<Variant>Badging` для release/beta/daily, использует `APK_FROM_BUNDLE` и `aapt2`. Сравнение идёт с [app-thunderbird/badging](../../app-thunderbird/badging) и [app-k9mail/badging](../../app-k9mail/badging); `build` зависит от check. Генерируемый путь: `<module>/build/outputs/apk_from_bundle/<variant>/<variant>-badging.txt`. Даже с `-x assemble -x build` это не чисто текстовая проверка: ей нужен APK-артефакт.
- [VersioningPlugin.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/app/versioning/VersioningPlugin.kt) регистрирует `printVersionInfo<Variant>`, использует merged manifest, ресурсы и version fields. `-PoutputFile=...` включает запись результата в указанный файл; release workflow использует `GITHUB_OUTPUT`.

## 3. Контроль качества и его границы

| Инструмент | Реальная конфигурация | Где разбирать проблему |
|---|---|---|
| Android Lint | [config/lint/lint.xml](../../config/lint/lint.xml) и Android conventions | `abortOnError=true`, `warningsAsErrors=false`, `checkDependencies=true`; `CI_CHECK_RELEASE_BUILDS` управляет `checkReleaseBuilds`. В XML есть отдельные уровни ошибок переводов и ресурсов |
| Detekt | [DetektPlugin.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/quality/detekt/DetektPlugin.kt), [config/detekt/detekt.yml](../../config/detekt/detekt.yml) | Compose rules, target 17; release в ignored build types, задачи с `androidHostTest` отключены; generated build directory исключён. Включены SARIF/Markdown reports, HTML/checkstyle выключены. `build` зависит от Detekt tasks; есть `detektAll` |
| Spotless | [SpotlessPlugin.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/quality/spotless/SpotlessPlugin.kt), [SpotlessExtension.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/quality/spotless/SpotlessExtension.kt), [.editorconfig](../../.editorconfig) | Kotlin ktlint, Gradle Kotlin scripts, Flexmark, trailing spaces `.gitignore`. В модулях Kotlin target задан для `src/*/kotlin`, не для всех файлов `src/*/java` |
| Kover/JaCoCo | [CodeCoveragePlugin.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/quality/coverage/CodeCoveragePlugin.kt), [CodeCoverageExtension.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/quality/coverage/CodeCoverageExtension.kt), [filters](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/quality/coverage/filter) | По умолчанию disabled; JaCoCo `0.8.14`, merge all projects у root, исключения generated/Compose/Android. Defaults branch/line 70/75, но root переопределяет их на 26/31; модули тоже задают свои значения |
| Dependency updates | [thunderbird.dependency.check.gradle.kts](../../build-plugin/src/main/kotlin/thunderbird.dependency.check.gradle.kts) | `dependencyUpdates` отклоняет нестабильного кандидата, если текущая версия стабильна; это поиск обновлений, не проверка baseline |
| Dependency Guard | Gradle-файлы приложений; каталоги `app-thunderbird/dependency-guard` и `app-k9mail/dependency-guard` в рабочем дереве отсутствуют | Не считать baseline доступным, пока каталоги не появятся; отличать его осознанный пересмотр от автоматического принятия изменений |
| Konsist | [quality/konsist/build.gradle.kts](../../quality/konsist/build.gradle.kts), [Scope.kt](../../quality/konsist/src/test/kotlin/net/thunderbird/quality/Scope.kt), [ValidateLogger.kt](../../quality/konsist/src/test/kotlin/net/thunderbird/quality/ValidateLogger.kt) | Проверки запрещённых imports `java.util.logging`, `android.util.Log`, Timber с конкретными исключениями. Это не полная автоматическая проверка всех API/internal границ. Toolchain 11 (Adoptium, Azul на Mac x64); up-to-date для `Test` отключён |

Flexmark root охватывает `*.md`, `docs/*.md`, `docs/**/*.md`; `.memory-bank/` явно не включена. Корневой Kotlin target охватывает `build-plugin/plugin/src/*/kotlin`, а Kotlin Gradle target не является рекурсивным охватом всех conventions. Не считать успешный `spotlessCheck` доказательством проверки любого файла репозитория.

Дополнительная оговорка coverage: plugin сначала задаёт convention из `CODE_COVERAGE_DISABLED`/`codeCoverageDisabled`, затем вызывает `initialize()`, где снова выполняется `disabled.convention(true)`. Это место требует проверки поведения Gradle Property перед доверием документированному `-PcodeCoverageDisabled=false`; здесь оно не исполнялось. Прямой DSL `codeCoverage { disabled.set(false) }` приведён в KDoc плагина. Поиск в workflow не обнаружил явных `kover*` вызовов или включения coverage.

## 4. Feature flags: конфигурация -> генерация -> тесты

1. Корневой build задаёт [schema](../../config/featureflag/thunderbird_mobile_featureflag.schema.json) и [catalog](../../config/featureflag/thunderbird_mobile_featureflag.catalog.json).
2. [FeatureFlagRootPlugin.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/featureflag/FeatureFlagRootPlugin.kt) после evaluation проверяет extension, JSON Schema и смысловые ограничения каталога. Использует `providers.fileContents`, чтобы configuration cache учитывал изменения файлов.
3. Валидаторы находятся в [featureflag/validator](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/featureflag/validator). Рядом существует отдельный [featureflag/schema/SchemaValidator.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/featureflag/schema/SchemaValidator.kt): в root plugin импортирован именно `validator.SchemaValidator`, не определять используемый класс только по имени.
4. [core/featureflag/build.gradle.kts](../../core/featureflag/build.gradle.kts) применяет library plugin. [FeatureFlagLibraryPlugin.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/featureflag/FeatureFlagLibraryPlugin.kt) разрешает применение только к `:core:featureflag`.
5. [GenerateFeatureFlagKeyEnumsTask.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/featureflag/task/GenerateFeatureFlagKeyEnumsTask.kt) регистрирует `generateFeatureFlagKeyEnums`: catalog -> [FeatureFlagKeyWriter.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/featureflag/codegen/FeatureFlagKeyWriter.kt) -> `build/generated/featureflags/src/commonMain/kotlin`. Provider добавляется в `commonMain`; генерация запускается как зависимость компиляции.
6. [GenerateFeatureFlagRawResTask.kt](../../build-plugin/plugin/src/main/kotlin/net/thunderbird/gradle/plugin/featureflag/task/GenerateFeatureFlagRawResTask.kt) копирует catalog в `build/generated/featureflags/res/raw/thunderbird_mobile_featureflag_catalog.json`; подключает generated resources к Android variants и raw directory к JVM main resources.

Тесты сборочной логики: [plugin/src/test/kotlin](../../build-plugin/plugin/src/test/kotlin). [FeatureFlagRootPluginTest.kt](../../build-plugin/plugin/src/test/kotlin/net/thunderbird/gradle/plugin/featureflag/FeatureFlagRootPluginTest.kt) проверяет отсутствующие/невалидные входы и format validation через ProjectBuilder. [GenerateFeatureFlagRawResTaskFunctionalTest.kt](../../build-plugin/plugin/src/test/kotlin/net/thunderbird/gradle/plugin/featureflag/task/GenerateFeatureFlagRawResTaskFunctionalTest.kt) через Gradle TestKit проверяет `UP_TO_DATE`, изменение каталога и `FROM_CACHE` после переноса временного тестового проекта. Такой тест сам запускает Gradle; это не разрешённая здесь «проверка без сборки».

Соседние места для регрессий: [CatalogValidatorTest.kt](../../build-plugin/plugin/src/test/kotlin/net/thunderbird/gradle/plugin/featureflag/validator/CatalogValidatorTest.kt), [FeatureFlagKeyWriterTest.kt](../../build-plugin/plugin/src/test/kotlin/net/thunderbird/gradle/plugin/featureflag/codegen/FeatureFlagKeyWriterTest.kt), [GenerateFeatureFlagRawResTaskTest.kt](../../build-plugin/plugin/src/test/kotlin/net/thunderbird/gradle/plugin/featureflag/task/GenerateFeatureFlagRawResTaskTest.kt). Эти файлы найдены как точки поиска, их тестовые тела целиком в этой работе не разобраны.

## 5. Где искать тесты приложения и E2E

Правила написания: [testing-guide.md](../../docs/contributing/testing-guide.md), [AGENTS.md](../../AGENTS.md): AAA, `testSubject`, assertk, fakes, Coroutines Test/Turbine. Наличие Mockito в version catalog не отменяет предпочтения fakes.

| Область | Существующие точки поиска |
|---|---|
| Общая KMP-логика | [core/validation/src/commonTest](../../core/validation/src/commonTest), [core/logging/api/src/commonTest](../../core/logging/api/src/commonTest) |
| JVM-specific | [core/file/src/jvmTest](../../core/file/src/jvmTest) |
| Android host tests | [core/logging/impl-file/src/androidHostTest](../../core/logging/impl-file/src/androidHostTest) |
| Test helpers | [core/testing](../../core/testing), [core/android/testing](../../core/android/testing), [core/ui/compose/testing](../../core/ui/compose/testing), [mail/testing](../../mail/testing), [backend/testing](../../backend/testing) |
| Архитектурная проверка imports | [quality/konsist/src/test](../../quality/konsist/src/test) |
| UI сценарии на установленном приложении | [quality/e2e/tests](../../quality/e2e/tests), переиспользуемые шаги [flows](../../quality/e2e/flows) |

Каталоги в таблице проверены на наличие; это навигация, а не заявление о полном чтении всех тестов. Для конкретного модуля проверить plugin и его `sourceSets` до выбора `test`, `jvmTest` или variant-specific task.

Maestro имеет отдельный поток: [launch_tfa_debug.sh](../../quality/e2e/launch_tfa_debug.sh) задаёт `MAESTRO_APP_ID=net.thunderbird.android.debug` -> [maestro_launcher.sh](../../quality/e2e/scripts/maestro_launcher.sh) разбирает флаги -> `maestro test` -> [init_with_account.yml](../../quality/e2e/tests/init_with_account.yml) выполняет [detect_variant.js](../../quality/e2e/scripts/detect_variant.js), `launchApp: clearState: true` и [onboard.yml](../../quality/e2e/tests/onboard.yml) -> onboarding flows.

Из каталога `quality/e2e` документирована команда:

```bash
./launch_tfa_debug.sh test tests/init_with_account.yml
```

Требуются установленное приложение нужного package, устройство/эмулятор и Maestro; [README](../../quality/e2e/README.md) указывает English (US), рекомендует Pixel 5/API 31+. Launcher не собирает и не устанавливает APK. Есть варианты Thunderbird daily/beta/release и K-9 debug/release. Общий сценарий [mail_configured_account_flow.yml](../../quality/e2e/tests/mail_configured_account_flow.yml) включает onboarding, отправку письма и работу с контактом. Это сценарии с изменением состояния приложения, а не read-only smoke.

Фактические параметры launcher: `--demo`, `--real`, `--email`, `--account-name`, `--user-name`, `test <file>`. Обещанный README alias `--display-name` в parser отсутствует. `detect_variant.js` выводит параметры аккаунта в лог; для демонстрации использовать тестовые данные. В просмотренных workflow вызов Maestro не найден.

## 6. CLI и каталог plugins

Корневая задача `buildCliTools` зависит от `build` пяти CLI. Все используют `ThunderbirdPlugins.App.jvm`; дополнительные настройки смотреть в [cli](../../cli). Wrapper scripts запускаются **из корня репозитория**: сначала `:cli:<name>-cli:installDist`, затем `cli/<name>-cli/build/install/<name>-cli/bin/<name>-cli` с исходными аргументами. Даже `scripts/<name> --help` сначала выполняет Gradle.

| CLI / wrapper | Точка входа и поток |
|---|---|
| [autodiscovery](../../scripts/autodiscovery) | [Main.kt](../../cli/autodiscovery-cli/src/main/kotlin/app/k9mail/cli/autodiscovery/Main.kt) -> [AutoDiscoveryCli.kt](../../cli/autodiscovery-cli/src/main/kotlin/app/k9mail/cli/autodiscovery/AutoDiscoveryCli.kt). Аргумент `email`, опции `--https-only`, `--include-email-address`; provider/ISPDB/MX lookup -> SerialRunner -> formatter. Выполняет сетевой поиск настроек |
| [html-cleaner](../../scripts/html-cleaner) | [Main.kt](../../cli/html-cleaner-cli/src/main/kotlin/app/k9mail/cli/html/cleaner/Main.kt): `HtmlCleaner`, аргументы UTF-8 input, app (`THUNDERBIRD` по умолчанию), optional output; `HtmlProcessor.processForDisplay` -> stdout/файл |
| [resource-mover](../../scripts/resource-mover) | [ResourceMoverCli.kt](../../cli/resource-mover-cli/src/main/kotlin/net/thunderbird/cli/resource/mover/ResourceMoverCli.kt) -> [StringResourceMover.kt](../../cli/resource-mover-cli/src/main/kotlin/net/thunderbird/cli/resource/mover/StringResourceMover.kt). Обязательные `--from`, `--to`, `--keys` через запятую. Меняет XML ресурсов; для этой карты не запускался |
| [translation](../../scripts/translation) | [TranslationCli.kt](../../cli/translation-cli/src/main/kotlin/net/thunderbird/cli/translation/TranslationCli.kt): обязательный `--token`, threshold по умолчанию 70%, `--print-all`, `--log-level`; Weblate -> языковые коды -> вывод `localeFilters` и `supported_languages` |
| [weblate](../../scripts/weblate) | [Main.kt](../../cli/weblate-cli/src/main/kotlin/net/thunderbird/cli/weblate/Main.kt) регистрирует `update/create/delete`; [WeblateCli.kt](../../cli/weblate-cli/src/main/kotlin/net/thunderbird/cli/weblate/WeblateCli.kt) собирает token/config/dry-run. [README](../../cli/weblate-cli/README.md), [managed-components.txt](../../cli/weblate-cli/managed-components.txt), [default-component-config.json](../../cli/weblate-cli/default-component-config.json) задают рабочую область управления компонентами |

`plugins/` не является вторым каталогом Gradle conventions. Здесь подключена Android-библиотека [openpgp-api/build.gradle.kts](../../plugins/openpgp-api-lib/openpgp-api/build.gradle.kts) с namespace `org.openintents.openpgp` и `aidl=true`; lifecycle/preference/fragment экспортируются через `api`, legacy logging подключён через `implementation`. Точки поиска: [Java/Kotlin API](../../plugins/openpgp-api-lib/openpgp-api/src/main/java/org/openintents/openpgp), [AIDL](../../plugins/openpgp-api-lib/openpgp-api/src/main/aidl/org/openintents/openpgp), [README](../../plugins/openpgp-api-lib/README.md). Реализация криптографического протокола в рамках этой карты не анализировалась.

## 7. CI: входы, команды и выходы

Общая подготовка: [.github/actions/setup-gradle/action.yml](../../.github/actions/setup-gradle/action.yml) копирует [.github/ci-gradle.properties](../../.github/ci-gradle.properties) в Gradle home, устанавливает Temurin 21 и настраивает cache. CI properties ограничивают workers четырьмя. [disk-cleanup](../../.github/actions/disk-cleanup/action.yml) и [disk-usage](../../.github/actions/disk-usage/action.yml) вызываются в common build; их наличие не означает требование запускать очистку на рабочем компьютере.

| Workflow | Когда / что выполняет |
|---|---|
| [build-android.yml](../../.github/workflows/build-android.yml) | Push main и PR с `paths-ignore`, включая `**.md`. `help` -> `:app-common:assemble`; зависимые jobs собирают оба приложения (`assemble -Pci=true`), проверяют badging, UI catalog в отдельной сборке components, `testsOnCi --parallel`, `lint` |
| [quality-checks.yml](../../.github/workflows/quality-checks.yml) | Push main и PR с аналогичным ignore. Отдельные jobs: `spotlessCheck`, `detekt`, `dependencyGuard`, `:quality:konsist:test`. KMP Detekt metadata tasks выполняются отдельным шагом с `continue-on-error: true` |
| [quality-markdown.yml](../../.github/workflows/quality-markdown.yml) | PR с `**.md`; `docs/install.sh`, `spotlessFlexmarkCheck`, `mdbook test docs`. Второй path в фильтре записан буквально `quality-markdown.yml`, без `.github/workflows/` |
| [build-cli-tools.yml](../../.github/workflows/build-cli-tools.yml) | Push main/PR только при `cli/**`; `buildCliTools`. Изменение только wrapper в `scripts/` не совпадает с этим paths filter |
| [build-scripts.yml](../../.github/workflows/build-scripts.yml) | Push main/PR по `scripts/**` или самому workflow, ручной запуск; Python 3.14, `bash scripts/test_python_scripts.sh` |
| [validate-gradle.yml](../../.github/workflows/validate-gradle.yml) | PR: Gradle wrapper-validation action. Не сборка приложения |
| [validate-workflows.yml](../../.github/workflows/validate-workflows.yml) | PR/ручной запуск: [validate-github-actions-pinned.sh](../../scripts/ci/validate-github-actions-pinned.sh), проверка SHA pinning actions |
| [security-codeql.yml](../../.github/workflows/security-codeql.yml) | Расписание/ручной запуск: actions, java-kotlin, python; для java-kotlin выполняется `assemble --no-daemon` |
| [security-fluidscan.yml](../../.github/workflows/security-fluidscan.yml) | Расписание/ручной запуск: [run-fluidattacks-scanners.sh](../../scripts/ci/run-fluidattacks-scanners.sh), [SAST config](../../config/fluidattacks/config-sast.yaml), [SCA config](../../config/fluidattacks/config-sca.yaml); SARIF artifacts и code scanning upload |
| [security-scorecard.yml](../../.github/workflows/security-scorecard.yml) | Main, расписание, branch protection/ручной запуск; Scorecard, `results.sarif`, публикация результатов |
| [build-docs.yml](../../.github/workflows/build-docs.yml) | Push main по `docs/**`/ручной запуск; только owner `thunderbird`. Test/build книги -> копирование в `gh-pages/docs/latest` -> commit/push |
| [build-daily.yml](../../.github/workflows/build-daily.yml) | Расписание/ручной запуск, owner `thunderbird`; вызывает reusable shippable workflow |
| [translation-update.yml](../../.github/workflows/translation-update.yml) | Расписание/ручной запуск, только upstream repo; Git export Weblate -> переводы `res/values-*`, `composeResources/values-*`, app metadata -> commit/ветка/PR |

Дополнительная навигация, без полного анализа каждого workflow: [.github/CODEOWNERS](../../.github/CODEOWNERS), [dependabot.yml](../../.github/dependabot.yml), [pull_request_template.md](../../.github/pull_request_template.md), [ISSUE_TEMPLATE](../../.github/ISSUE_TEMPLATE), [validate-milestones.yml](../../.github/workflows/validate-milestones.yml), [pr-request-report-labels.yml](../../.github/workflows/pr-request-report-labels.yml). Остальные `pr-*`/`issue-*` workflow управляют сотрудничеством; они не являются тестами почтового клиента.

### Release workflow отдельно от проверок PR

[shippable_builds.yml](../../.github/workflows/shippable_builds.yml) содержит цепочку `get_environment` -> `dump_config` -> `release_commit` -> `build_unsigned` -> `sign_mobile` -> `pre_publish` -> `publish_release`, а также уведомления. Это обзор основных jobs, не полный граф всех `needs` и условий.

- Ветки `main/beta/release` отображаются в release environments; matrix приходит из `MATRIX_INCLUDE` и включает app/flavor/format/targets.
- Version info и release notes используются для bump/commit. Сборка unsigned: `clean :app-<app>:<assemble|bundle><Flavor><Type> --no-build-cache`; K-9 использует release type и для beta канала.
- `testsOnCi` вызывается, если не задан `skipTests`. Unsigned artifacts передаются в signing job; затем загружаются signed APK/AAB.
- `publish_hold` служит environment gate для beta/release; наличие реальной protection rule задаётся настройками GitHub, которых локальный YAML не доказывает.
- Публикация поддерживает GitHub Releases, Play и GCS/FTP; предусмотрены skip/draft/stage inputs. Это инфраструктура upstream, не готовая настройка собственного бренда.

Разобраны основные входы и jobs сборки/подписи; весь большой release workflow построчно не прочитан. Никакие workflow, публикации и уведомления не запускались.

## 8. Справочник точных команд

Рабочий каталог для таблицы: `C:\Projects\Mail\thunderbird-android`. Windows использует `.\gradlew.bat`; CI/Bash использует `./gradlew`. Команды приведены для последующей работы в подготовленном окружении. **В этой задаче они не выполнялись.**

| Команда | Источник и назначение |
|---|---|
| `.\gradlew.bat :app-thunderbird:assembleDebug` | [development-environment.md](../../docs/contributing/development-environment.md): debug приложения |
| `.\gradlew.bat test lint detekt spotlessCheck` | [AGENTS.md](../../AGENTS.md): минимальный набор при bugfix; не заменять одним `testsOnCi` |
| `.\gradlew.bat testsOnCi --parallel` | Android CI и conventions; host tests по правилам раздела 2 |
| `.\gradlew.bat connectedAndroidTest` | Руководство тестирования; нужны соответствующие тесты и подключённое устройство/эмулятор |
| `.\gradlew.bat :quality:konsist:test` | Quality CI, отдельный JVM toolchain 11 |
| `.\gradlew.bat dependencyGuard` | Quality CI, проверка classpath baselines |
| `.\gradlew.bat dependencyUpdates` | Build-plugin README и dependency convention |
| `.\gradlew.bat buildCliTools` | Корневой build и CLI CI, пять CLI builds |
| `.\gradlew.bat spotlessFlexmarkCheck` | Markdown CI; область памяти не включена в target |
| `.\gradlew.bat detektMetadataCommonMain` / `detektMetadataMain` / `detektMetadataCommonJvmMain` | Три отдельных вызова KMP metadata checks из Quality CI; там failure шага допускается |
| `.\gradlew.bat :app-k9mail:checkFossReleaseBadging :app-k9mail:checkFullReleaseBadging -x assemble -x build` | Точная группа K-9 из Android CI |
| `.\gradlew.bat :app-thunderbird:checkFossBetaBadging :app-thunderbird:checkFossDailyBadging :app-thunderbird:checkFossReleaseBadging :app-thunderbird:checkFullBetaBadging :app-thunderbird:checkFullDailyBadging :app-thunderbird:checkFullReleaseBadging -x assemble -x build` | Точная группа Thunderbird из Android CI |
| `.\gradlew.bat -p components :ui:catalog:assembleDebug` | Android CI; каталог дизайна принадлежит included build components |
| `.\gradlew.bat -p build-plugin :plugin:test` | Выведено из `build-plugin/settings.gradle.kts` и Kotlin DSL plugin-модуля; отдельный запуск тестов build logic, не найден как явная команда workflow |
| `mdbook test docs` / `mdbook build docs` / `mdbook serve docs --open` | Markdown CI, docs guide; три отдельные операции test/build/preview |
| `bash scripts/test_python_scripts.sh` | Scripts CI: временный venv, pip install, четыре `py_compile`; не behavioral unit tests |
| `powershell -NoProfile -File scripts/project/Check-Memory.ps1` | Проверка файлов/регистрации/локальных inline-ссылок всей памяти; новая карта требует интеграции общих индексов другим исполнителем |
| `powershell -NoProfile -File scripts/project/Test-Environment.ps1 -Json` | Диагностика Git, Java, Wrapper, SDK/platform; всегда `buildVerified=false` |
| `powershell -NoProfile -File scripts/project/tests/Test-Memory.ps1` / `powershell -NoProfile -File scripts/project/tests/Test-Environment.Tests.ps1` | Тесты инструментов памяти/окружения, не Android-тесты |

`spotlessApply`, `dependencyGuardBaseline` и `update<Variant>Badging` изменяют файлы и не являются проверками без записи. Команды сборки могут создавать generated sources и скачивать зависимости. Не запускать их для простого перехода по карте.

Пути результатов, заданные прямо в коде/CI: CLI distributions `cli/<name>/build/install/<name>/bin/`; feature flags `core/featureflag/build/generated/featureflags/`; badging `app-*/build/outputs/apk_from_bundle/`; APK `app-*/build/outputs/apk/<flavor>/<type>/`, Thunderbird AAB `app-thunderbird/build/outputs/bundle/<flavor><Type>/`; книга `book/docs/latest/`. Это ожидаемые выходы, а не ссылки на якобы существующие артефакты текущего запуска. HTML/XML coverage paths в testing guide требуют отдельной сверки с фактическим variant и версией Kover.

## 9. Документация, конфигурации и служебные scripts

### Книга docs

Начало навигации: [docs/README.md](../../docs/README.md), [CONTRIBUTING.md](../../docs/CONTRIBUTING.md), [SUMMARY.md](../../docs/SUMMARY.md). `SUMMARY.md` является оглавлением mdBook, не автоматическим перечислением всех файлов.

| Раздел | Для чего открывать |
|---|---|
| [contributing](../../docs/contributing) | Окружение, workflow, тесты, качество, переводы, review и Git conventions |
| [architecture](../../docs/architecture) | Организация модулей, UI, темы, feature flags и legacy integration |
| [engineering](../../docs/engineering) | ADR, RFC, technical designs, user journeys, delivery planning. Статус Proposed/Accepted смотреть в документе, не выводить из имени |
| [developer](../../docs/developer) | Migration checklists, foldables, mobile components |
| [release](../../docs/release) | Процесс upstream, automation, ручные [testing-checklist.md](../../docs/release/testing-checklist.md) и [developer-checklist.md](../../docs/release/developer-checklist.md) |
| [security](../../docs/security), [user-guide](../../docs/user-guide) | Threat modeling/incident response и пользовательская диагностика |

[book.toml](../../docs/book.toml): `src="."`, `build-dir="../book/docs/latest"`, `create-missing=false`, тема [assets/tb-theme](../../docs/assets/tb-theme), дополнительные CSS/JS в [assets/additional](../../docs/assets/additional). Активные preprocessors: last-changed и mermaid. [install.sh](../../docs/install.sh) устанавливает mdBook `0.5.2`, last-changed `0.4.0`, mermaid preprocessor `0.17.0`; Mermaid.js указан `v11.14.0`. Скрипт может писать в assets, поэтому он не запускался. [HOW-TO-DOCUMENT.md](../../docs/HOW-TO-DOCUMENT.md) объясняет регистрацию новых страниц в SUMMARY.

### Config и schemas

- [config/weblate/README.md](../../config/weblate/README.md) объясняет служебный base component; существующий placeholder расположен в [composeResources/values/strings.xml](../../config/weblate/composeResources/values/strings.xml). Это конфигурационный ресурс, не самостоятельный модуль приложения.
- Feature flag schema/catalog описаны в разделе 4. Lint, Detekt и Fluidattacks configs связаны со своими потребителями в разделах 3 и 7.
- [changelog-release.schema.json](../../schemas/changelog-release.schema.json) задаёт `schemaVersion=1`, version/versioncode/date/notes, типы notes `new/changed/fixed`, optional issues/pullRequests/featureFlags/source. [changelog-index.schema.json](../../schemas/changelog-index.schema.json) задаёт releases с `resourceName` формата `changelog_release_*`. Обе запрещают лишние свойства на соответствующих уровнях.
- [generate_changelog_json.py](../../scripts/changelog/generate_changelog_json.py): CLI `applicationid version versioncode [--repository ...] [--branch ...]`; release notes YAML -> JSON release -> индекс в `app-*/src/<build_type>/res/raw/`, validation через `Draft202012Validator`. [migrate_changelog_to_json.py](../../scripts/changelog/migrate_changelog_to_json.py): CLI `applicationid`, вход `changelog_master.xml` из того же raw-каталога -> JSON files/index. В этой работе разобраны CLI/main, пути и точки validation, не все преобразования текста.

### Scripts и границы побочных действий

| Группа | Точки входа и ограничения |
|---|---|
| Python окружение | [README](../../scripts/README.md), [requirements.txt](../../scripts/requirements.txt), [test_python_scripts.sh](../../scripts/test_python_scripts.sh). Скрипт устанавливает зависимости и компилирует `render-notes.py`, `setup_release_automation`, `merge_gradle.py`, `contributor_list.py`; не покрывает все Python-файлы, в частности changelog scripts |
| Release tooling | [render-notes.py](../../scripts/ci/render-notes.py), [templates](../../scripts/ci/templates), [setup_release_automation](../../scripts/ci/setup_release_automation), [contributor_list.py](../../scripts/ci/contributor_list.py). Навигация к подготовке notes/environments; не доказательство исправности GitHub API операций |
| Merge и отчёты | [merge_gradle.py](../../scripts/ci/merges/merge_gradle.py), [do_merge.sh](../../scripts/ci/merges/do_merge.sh), [merged-pr-report.sh](../../scripts/merged-pr-report.sh), [MERGED_PR_REPORT_README.md](../../scripts/MERGED_PR_REPORT_README.md). Найденные утилиты, внутренние алгоритмы здесь не анализировались полностью |
| Локальная память | [Check-Memory.ps1](../../scripts/project/Check-Memory.ps1): recursive traversal, непустота, регистрация каждого файла в CLAUDE/README, inline Markdown links; не проверяет reference links, HTML и якоря. Exit 1 при ошибках |
| Диагностика Windows | [Test-Environment.ps1](../../scripts/project/Test-Environment.ps1): `-ProjectRoot`, `-Json`; проверяет Java major **равно** `.java-version`, SDK directory и platform android.jar; не проверяет полную работоспособность toolchain. Его правило Java уже, чем Java 21+ в settings |
| Device helpers | [app_cycler.sh](../../scripts/app_cycler.sh) перезапускает package через ADB; [take_screenshot.sh](../../scripts/take_screenshot.sh) включает SystemUI demo mode и пишет PNG; [extract-eml.sh](../../scripts/extract-eml.sh) выгружает EML и по умолчанию удаляет их с устройства, `--keep` оставляет. Это не unit tests |
| Историческая сборка | [debian_build.sh](../../scripts/debian_build.sh) содержит Debian 8, SDK r24.3.3 и старые пути `k9mail/`; не использовать как актуальный рецепт для нынешнего Gradle/SDK |

## 10. Что в существующих документах расходится с файлами

Сверка локальная: внешние страницы и состояние удалённых CI/магазинов не проверялись. Исправления ниже зафиксированы только в этой карте; исходные документы не редактировались.

| Документ / утверждение | Фактическое уточнение |
|---|---|
| [build-plugin/README.md](../../build-plugin/README.md): `thunderbird.quality.detekt/spotless/badging` | Текущие регистрации находятся в `plugin/build.gradle.kts`: `net.thunderbird.gradle.plugin.quality.detekt`, `.quality.spotless`, `.app.badging`. Список conventions README также не охватывает нынешние KMP/JVM app scripts |
| [code-quality-guide.md](../../docs/contributing/code-quality-guide.md): старые `thunderbird.quality.*.gradle.kts` и workflow `android.yml` | Нынешние точки входа: классы DetektPlugin/SpotlessPlugin и `.github/workflows/build-android.yml`. Старые файлы в дереве не найдены |
| [testing-guide.md](../../docs/contributing/testing-guide.md): E2E и manual scripts отсутствуют | Есть Maestro `quality/e2e` и release testing checklist. Их наличие не доказывает автоматический запуск в CI |
| Тот же testing guide: coverage enforced in CI, минимум 70/75 | Plugin по умолчанию disabled, root 26/31; явный запуск Kover в workflow не найден. Дополнительно см. повторный `disabled.convention(true)` в разделе 3 |
| [quality/e2e/README.md](../../quality/e2e/README.md): `setup/handle_permissions.yml`, `--display-name`, defaults | Каталог setup не найден; реальный permission flow в `flows/onboarding/permissions.yml`; alias не реализован; JS default account name `Demo User Account`, email `demo-user@example.com` |
| [HOW-TO-DOCUMENT.md](../../docs/HOW-TO-DOCUMENT.md): external-links и pagetoc в списке extensions | Установщик ставит только mdbook, last-changed, mermaid; book.toml явно регистрирует два последних preprocessors. Наличие theme assets не означает установленного preprocessor |
| [config/weblate/README.md](../../config/weblate/README.md): `composeResources/values/values.xml` | Фактическое имя файла `strings.xml` |
| Вывод [TranslationCli.kt](../../cli/translation-cli/src/main/kotlin/net/thunderbird/cli/translation/TranslationCli.kt): `docs/translating.md` | Такой путь не найден; точки навигации сейчас [translations.md](../../docs/contributing/translations.md) и [managing-strings.md](../../docs/contributing/managing-strings.md) |
| Wrapper и version catalog | `9.7.0` против `9.6.1`; не исправлено этой задачей. Повторный запуск `wrapper` может сменить дистрибутив на значение каталога |

## 11. Быстрый поиск при следующем изменении

Все команды ниже только ищут текст/имена; выполнять из корня. Это способ продолжения анализа, не замена прочтению найденных файлов.

```powershell
rg -n 'includeBuild|include\(|tb.components.local' settings.gradle.kts
rg -n 'testsOnCi|sourceSets|withHostTest|beforeVariants' build-plugin/src/main/kotlin
rg -n 'testImplementation|commonTest|androidHostTest|jvmTest' gradle/libs.versions.toml build-plugin core quality cli
rg -n 'run:|gradlew|continue-on-error|paths-ignore' .github/workflows
rg -n 'register|TASK_NAME|outputDirectory|outputResDir' build-plugin/plugin/src/main/kotlin
rg --files quality build-plugin/plugin/src/test cli scripts docs
```

Практические ориентиры: сначала проверить include/тип plugin, затем source set, затем команду CI, затем ограничения охвата, затем фактический отчёт запуска. Без этого легко выбрать задачу другого варианта, принять generated код за исходник или успешный formatter за проверку тестов. При обновлении upstream эти пять мест требуют повторной сверки.

## 12. Глубина чтения и проверка этой карты

Ключевые файлы, содержимое которых прочитано целиком для данной карты:

- `settings.gradle.kts`; `build-plugin/settings.gradle.kts`, `build-plugin/build.gradle.kts`, `build-plugin/plugin/build.gradle.kts`, `build-plugin/README.md`; все файлы непосредственно в `build-plugin/src/main/kotlin` (conventions и helpers).
- `SpotlessPlugin.kt`, `DetektPlugin.kt`, `CodeCoveragePlugin.kt`, `CodeCoverageExtension.kt`; `FeatureFlagRootPlugin.kt`, `FeatureFlagLibraryPlugin.kt`, `GenerateFeatureFlagKeyEnumsTask.kt`, `GenerateFeatureFlagRawResTask.kt`; `BadgingPlugin.kt`, `VersioningPlugin.kt` по ссылкам выше.
- `FeatureFlagRootPluginTest.kt`, `GenerateFeatureFlagRawResTaskFunctionalTest.kt`; три файла `quality/konsist`; `core/featureflag/build.gradle.kts`; `plugins/openpgp-api-lib/openpgp-api/build.gradle.kts`.
- Пять `cli/*/build.gradle.kts`, пять `scripts/<cli>` wrapper; `AutoDiscoveryCli.kt`, HTML cleaner `Main.kt`, `ResourceMoverCli.kt`, `TranslationCli.kt`, Weblate `Main.kt`/`WeblateCli.kt` и `cli/weblate-cli/README.md`.
- `quality/e2e/README.md`, `launch_tfa_debug.sh`, `maestro_launcher.sh`, `detect_variant.js`, `tests/init_with_account.yml`, `tests/onboard.yml`, `tests/mail_configured_account_flow.yml`.
- Workflow `build-android`, `quality-checks`, `build-cli-tools`, `build-scripts`, `quality-markdown`, `build-docs`, `build-daily`, `translation-update`, `validate-gradle`, `validate-workflows`, `security-codeql`, `security-fluidscan`, `security-scorecard`; action `setup-gradle` и `ci-gradle.properties`.
- `docs/contributing/development-environment.md`, `testing-guide.md`, `code-quality-guide.md`, ADR-0005, `docs/HOW-TO-DOCUMENT.md`, `docs/book.toml`, `docs/install.sh`, `docs/README.md`, `docs/SUMMARY.md`; обе changelog JSON schemas; lint XML и Fluidattacks YAML; `config/weblate/README.md`.
- `scripts/README.md`, `test_python_scripts.sh`, `scripts/project/Check-Memory.ps1`, `scripts/project/Test-Environment.ps1`.

Частично разобраны: `gradle/libs.versions.toml` (versions и найденные test bundles/aliases), Gradle-файлы приложений (variants/guard), release workflow (основные jobs и команды), changelog Python (main/CLI/пути/validation), release docs. Другие приведённые пути найдены/проверены как навигация; это не полное чтение соответствующих деревьев. Инструкции CLAUDE/AGENTS и память проекта использованы как контекст, а не доказательство текущего поведения сборки.

Границы результата: Gradle, mdBook, Python tooling, Maestro, device tests и CI не запускались по условиям задачи; неизвестны фактический task graph, успешность compilation, runtime тестов и публикаций. Запись памяти о недоступной Java относится к прежним проверкам; новая попытка сборки не предпринималась.

Проверка назначенного Markdown: все inline-ссылки должны разрешаться от этого каталога через `../../` к существующему файлу/каталогу репозитория; generated outputs намеренно указаны текстом. Проверяются также непустота, UTF-8, пробелы на концах строк и завершающий перевод строки. Общие карты и журналы не изменяются; регистрация документа в общих индексах остаётся у ответственного за интеграцию карты кода.
