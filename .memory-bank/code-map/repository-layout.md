# Дерево и границы проекта

Источник включения модулей: [settings.gradle.kts](../../settings.gradle.kts). Наличие папки само по себе не делает её самостоятельным Gradle-модулем.
Полные пути искать через `rg --files` и `git ls-files`; отдельный каталог `docs/code-map` в проекте пока не создан.

| Папка | Что находится внутри | Где продолжить |
|---|---|---|
| [app-thunderbird](../../app-thunderbird) | Точка сборки целевого приложения, бренд, manifest, варианты, OAuth | Страница `apps-and-ui.md` ещё не создана |
| [app-k9mail](../../app-k9mail) | Второй продукт на общей основе | Страница `apps-and-ui.md` ещё не создана |
| [app-common](../../app-common) | Связывание общих компонентов, Android-ресурсы и зависимости приложений | Страница `apps-and-ui.md` ещё не создана |
| [app-metadata](../../app-metadata) | Материалы приложения для каналов распространения, локализованные описания | Проверять вместе с брендингом, это не тексты экранов |
| [feature](../../feature) | Аккаунты, почтовые экраны, вводная настройка, уведомления, миграция и прочие функции | Интерфейсная страница ещё не создана; см. [почту](mail-and-storage.md) |
| [core](../../core) | Общие API, настройки, логи, доступ к Android и UI-инфраструктура | [Общие модули](core-and-components.md) |
| [components](../../components) | Отдельная сборка переиспользуемых компонентов Bolt, каталога UI и поддержки тестов | [Общие модули](core-and-components.md) |
| [backend](../../backend) | Реализации почтовых backend, API и тестовые реализации | [Почта](mail-and-storage.md) |
| [mail](../../mail) | Общие почтовые модели, MIME и протоколы IMAP/POP3/SMTP | [Почта](mail-and-storage.md) |
| [legacy](../../legacy) | Действующая унаследованная логика, хранение, сообщения, шифрование и экраны | См. [почту](mail-and-storage.md); интерфейсная страница ещё не создана |
| [library](../../library) | HTML-cleaner и token-auto-complete | [Почта](mail-and-storage.md) |
| [ui-utils](../../ui-utils) | Утилиты старых View: прокрутка, жесты, нижний диалог | [Общие модули](core-and-components.md) |
| [plugins](../../plugins) | Включённый код OpenPGP API | [Сборка](build-and-tests.md) |
| [build-plugin](../../build-plugin) | Gradle-плагины и соглашения настройки модулей | [Сборка](build-and-tests.md) |
| [gradle](../../gradle) | Wrapper, каталог версий и настройки Java toolchain | [Сборка](build-and-tests.md) |
| [quality](../../quality) | Проверки архитектуры и соглашений Konsist | [Сборка](build-and-tests.md) |
| [cli](../../cli) | Командные инструменты разработчика | [Сборка](build-and-tests.md) |
| [scripts](../../scripts) | Скрипты разработки, выпуска и наши локальные проверки | [Сборка](build-and-tests.md) |
| [config](../../config) | Настройки анализаторов и других инструментов | [Сборка](build-and-tests.md) |
| [schemas](../../schemas) | Схемы структурированных данных проекта | [Сборка](build-and-tests.md) |
| [docs](../../docs) | Исходные руководства, архитектурные решения, документация выпуска | Начать с [CONTRIBUTING](../../docs/CONTRIBUTING.md) |
| [images](../../images) | Графические материалы репозитория | Не подменять ими ресурсы APK без проверки |
| [.github](../../.github) | CI, шаблоны и действия GitHub | [Сборка](build-and-tests.md) |
| [.idea](../../.idea) | Отслеживаемые настройки IDE и шаблоны | Не путать с исходниками приложения |
| [.memory-bank](../README.md) | Правила и память собственной разработки | Главная карта памяти |

Корневые файлы: `README.md` и `AGENTS.md` описывают проект и правила; `CLAUDE.md` - наш процесс; `build.gradle.kts` и `settings.gradle.kts` - сборка; `gradle.properties`, `.java-version`, `mise.toml`, `gradle-remote`, `gradlew`, `gradlew.bat` - инструменты и окружение; `LICENSE`, `NOTICE`, `SECURITY.md`, `CODE_OF_CONDUCT.md` - исходные условия и политики; `.editorconfig`, `.gitattributes`, `.gitignore` - правила файлов; `metadata` - отдельный файл корня, не папка `app-metadata`.

## Связи на уровне сборки

```text
app-thunderbird / app-k9mail
  -> app-common и выбранные feature/core/legacy реализации
feature -> общие API и инфраструктура (конкретно: build.gradle.kts модуля)
backend -> почтовые реализации и API
mail -> протоколы и общие почтовые типы
components -> отдельная сборка, подстановка библиотек по настройкам settings
build-plugin -> соглашения сборки, не пользовательская функция APK
```

Это обзор направлений, не полный вычисленный граф Gradle. Конкретные зависимости проверять в файлах сборки, учитывая общие плагины и варианты.
