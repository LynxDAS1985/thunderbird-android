# Как быстро искать место изменения

Команды ниже выполняются из корня репозитория. Сначала искать в конкретной области, затем расширять поиск. Не искать в `.git` или сборочных результатах.

## Типовые задачи

| Нужно изменить | Начальная область | Что проверить рядом |
|---|---|---|
| Имя, иконка, ссылки поддержки | `app-thunderbird/src`, `app-thunderbird/build.gradle.kts`, `app-metadata` | Варианты debug/release, тема, экран о приложении, материалы магазина |
| Вход в почту | `feature/account`, `feature/autodiscovery`, конфигурации OAuth приложения | Редирект, проверка сервера, сохранение аккаунта, тесты |
| Список писем / чтение / написание | `feature/mail/message`, `legacy/ui` | Флаг функции, маршрут навигации, текущая реализация экрана |
| Отправка / получение | `backend`, `mail`, `legacy/core` | Протокол, хранилище и связь с UI |
| Уведомление о письме | `feature/notification`, общие notification preferences | Разрешение Android, настройки аккаунта, вызовы отправителя |
| Цвет, размер кнопки, компонент | `components/ui/bolt`, `core/ui/legacy` | Compose или View, светлая/тёмная тема |
| Общая настройка | `core/preference`, `core/configstore`, `feature/settings` | API, реализация, миграция, наблюдатели |
| Перенос аккаунтов | `feature/migration`, `feature/onboarding`, `feature/settings/import` | Формат, проверка данных и исходное приложение |
| Виджет | `feature/widget` | Manifest, провайдер, источник данных |
| Сборка и версии библиотек | `build-plugin`, `gradle/libs.versions.toml`, `settings.gradle.kts` | Оба приложения и отдельная сборка components |

## Готовые команды

```powershell
# Найти класс или реализацию во всех основных слоях.
rg -n 'class .*ThemeManager|interface ThemeManager' core app-common app-thunderbird

# Найти прямых потребителей символа (декларация тоже попадёт в вывод).
rg -n '\bGeneralSettingsManager\b' app-common app-thunderbird core feature legacy

# Найти файлы экранов, моделей состояния и тестов в нужной области.
rg --files feature/mail -g '*Screen*' -g '*ViewModel*' -g '*Test*'

# Найти регистрацию компонентов и зависимости Gradle.
rg -n 'single|factory|viewModel|includes' app-common/src app-thunderbird/src -g '*.kt'
rg -n 'projects\.|implementation|api\(' feature/mail -g 'build.gradle.kts'

# Найти строку интерфейса, затем её ключ в коде.
rg -n 'app_name' app-thunderbird core feature legacy -g '*.xml' -g '*.kt' -g '*.java'

# Найти варианты и тестовые наборы.
rg --files app-thunderbird -g '*Manifest.xml' -g '*OAuth*' -g '*Test*'

# Найти точный файл даже вне Kotlin/Java.
git ls-files '*ThemeManager*'

# Все файлы конкретной папки сборки из полного каталога.
Import-Csv docs/code-map/files.csv | Where-Object { $_.moduleDirectory -eq 'core/ui/theme/manager' }
```

Это рецепты поиска, не команды изменения. Результат `rg` - список кандидатов; он не доказывает вызов во время работы. Для этого проверить wiring приложения, флаги и вариант сборки.

## Добавление файла и обновление карты

Новые неотслеживаемые файлы видны через `git status --short --untracked-files=all`, но не входят в результат `git ls-files`. Отдельный каталог `docs/code-map` и инструкция его пересоздания в проекте пока отсутствуют.
Секреты, `local.properties`, ключи подписи и временные файлы не добавлять ради полноты каталога.
