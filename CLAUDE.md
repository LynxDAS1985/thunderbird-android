# Правила работы над Mail для Android

Mail - рабочее название проекта. Основа: Thunderbird for Android; название собственного бренда ещё не выбрано.
**Текущая версия / Current version**: 24.0 (versionCode 33).
Корень Git и всех команд: `C:\Projects\Mail\thunderbird-android`.

## Начало каждой сессии

1. Прочитать этот файл, [`AGENTS.md`](AGENTS.md), [`AI_INSTRUCTIONS.md`](AI_INSTRUCTIONS.md) и все файлы в `.memory-bank/`, включая вложенные.
2. Проверить состояние Git и реальные файлы проекта. При расхождении исправить память по фактам (код живой — доверяй коду).
3. **Правило 3 фактов перед изменением кода**:
   - 🥇 Официальная документация Google / Kotlin под точную используемую версию.
   - 🥈 Код текущего репозитория (реальные файлы и строки).
   - 🥉 Память проекта ([`.memory-bank/`](.memory-bank/README.md) и принятые ADR).
4. **Критические запреты для Android-проекта**:
   - ⛔ Никаких скрытых откатов (`git reset --hard`, `git checkout .`, `git revert`) без прямого приказа пользователя.
   - ⛔ Не запускать долгие сборки всего проекта (`./gradlew build`) или эмуляторы в фоне без явного согласия (в проекте 50+ модулей; запускать только точечные тесты или `:app-thunderbird:assembleFullDebug`).
   - ⛔ Не использовать `git add .` или `git add -A` (риск утечки `.jks`, `.keystore`, `.env`, `local.properties`).
   - ⛔ Не использовать `GlobalScope` и пустые `try-catch`.
   - ⛔ Не менять схемы Room и хранилищ настроек без миграции и инкремента версии.
   - ⛔ Нулевая утечка PII: запрещено выводить в логи адреса email и имена пользователей.
5. По-русски объяснить задачу, план и критерий готовности. Неясности, влияющие на результат, уточнить до зависимых действий.
6. Независимые задачи выполнять параллельно, когда это ускоряет работу; агентам назначать непересекающиеся области. Не запускать несколько сборок одного рабочего каталога одновременно.
7. Результаты независимых аудитов разными моделями ИИ заносить в [`AUDIT II.md`](AUDIT%20II.md) по правилам [`AUDIT-REGISTRY.md`](AUDIT-REGISTRY.md) (строго Append-Only).

## Карта всех файлов памяти

|                                                  Файл                                                  |               Назначение                |
|--------------------------------------------------------------------------------------------------------|-----------------------------------------|
| [.memory-bank/README.md](.memory-bank/README.md)                                                       | Карта и правила ведения памяти          |
| [.memory-bank/projectbrief.md](.memory-bank/projectbrief.md)                                           | Цель, границы и исходная версия         |
| [.memory-bank/architecture.md](.memory-bank/architecture.md)                                           | Устройство Android-проекта              |
| [.memory-bank/coding-rules.md](.memory-bank/coding-rules.md)                                           | Правила кода и приватности              |
| [.memory-bank/workflow.md](.memory-bank/workflow.md)                                                   | Порядок выполнения задач                |
| [.memory-bank/testing.md](.memory-bank/testing.md)                                                     | Проверки и состояние окружения          |
| [.memory-bank/deployment.md](.memory-bank/deployment.md)                                               | Git, выпуск Android и сервер            |
| [.memory-bank/branding.md](.memory-bank/branding.md)                                                   | Подготовка собственного бренда          |
| [.memory-bank/decisions.md](.memory-bank/decisions.md)                                                 | Принятые решения и причины              |
| [.memory-bank/common-mistakes.md](.memory-bank/common-mistakes.md)                                     | Ошибки и предотвращение повторений      |
| [.memory-bank/features.md](.memory-bank/features.md)                                                   | Фактически реализованные изменения      |
| [.memory-bank/code-todo.md](.memory-bank/code-todo.md)                                                 | Следующие задачи и блокировки           |
| [.memory-bank/work-log.md](.memory-bank/work-log.md)                                                   | Учёт сессий и результаты проверок       |
| [.memory-bank/CHANGELOG.md](.memory-bank/CHANGELOG.md)                                                 | Краткая история изменений               |
| [.memory-bank/user-updates.md](.memory-bank/user-updates.md)                                           | Правила уведомлений пользователям       |
| [.memory-bank/project-improvements.md](.memory-bank/project-improvements.md)                           | Десять вариантов улучшений и выбор пяти |
| [.memory-bank/verification-matrix.md](.memory-bank/verification-matrix.md)                             | Проверки по типу изменения              |
| [.memory-bank/task-template.md](.memory-bank/task-template.md)                                         | Критерии готовности и передача задачи   |
| [.memory-bank/release-checklist.md](.memory-bank/release-checklist.md)                                 | Доказательства готовности выпуска       |
| [.memory-bank/code-map/README.md](.memory-bank/code-map/README.md)                                     | Индекс навигатора по исходному коду     |
| [.memory-bank/code-map/repository-layout.md](.memory-bank/code-map/repository-layout.md)               | Дерево каталогов и границы модулей      |
| [.memory-bank/code-map/core-and-components.md](.memory-bank/code-map/core-and-components.md)           | Карта общих модулей и компонентов       |
| [.memory-bank/code-map/mail-and-storage.md](.memory-bank/code-map/mail-and-storage.md)                 | Карта почты, протоколов и хранения      |
| [.memory-bank/code-map/build-and-tests.md](.memory-bank/code-map/build-and-tests.md)                   | Карта сборки, проверок и выпуска        |
| [.memory-bank/code-map/search-guide.md](.memory-bank/code-map/search-guide.md)                         | Практические рецепты поиска по проекту  |
| [.memory-bank/sender-avatars/findings-and-fixes.md](.memory-bank/sender-avatars/findings-and-fixes.md) | Замечания N1-N9 и план их устранения    |

Новый файл памяти обязательно добавить в эту таблицу и `.memory-bank/README.md` в той же задаче.
Память хранит факты, ссылки на код и статусы, но не пароли, токены, письма или ключи подписи.
Правила соседних проектов служат образцом организации, а не инструкциями для этого приложения.

## Завершение задачи

1. Проверить результат и существующие Linter/Unit-tests до коммита; для новой логики добавить содержательные тесты.
   Выбрать проверки по `.memory-bank/verification-matrix.md`. Проверка памяти не заменяет Gradle или проверку APK.
2. Обновить память, журнал работ и историю изменений. Отдельно назвать невыполненные проверки и причины.
3. Для изменений, заметных конечному пользователю, подготовить «Обновление системы» по `user-updates.md`.
4. Выполнить запрошенные Commit + Push + Deploy при наличии нужных адресов, окружения и успешных обязательных проверок. Не считать локальный коммит публикацией и не отправлять изменения в официальный upstream вместо репозитория владельца.
5. Docker пересобирать только для установленного серверного компонента этого проекта. APK не обновляется на телефоне от перезапуска Docker.
6. Дать минимум пять относящихся к задаче советов простыми словами: польза, последствия отказа и возможные трудности внедрения.
7. Закончить разделом «Что сделано в этой сессии» и таблицей: `Задача | Файлы изменения | Статус теста | Влияние на систему`.

# Дополнительная карта проекта

- [`.memory-bank/sender-avatars.md`](.memory-bank/sender-avatars.md) — точки внедрения аватаров и проверенных логотипов отправителей.
- [`.memory-bank/sender-avatars/README.md`](.memory-bank/sender-avatars/README.md) — индекс тематической памяти аватаров отправителей.
- [`.memory-bank/sender-avatars/implementation-plan.md`](.memory-bank/sender-avatars/implementation-plan.md) — полный поэтапный план реализации.
- [`.memory-bank/sender-avatars/phone-verification.md`](.memory-bank/sender-avatars/phone-verification.md) — журнал проверки этапов на Android-телефоне.
- [`.memory-bank/sender-avatars/mockups.md`](.memory-bank/sender-avatars/mockups.md) — каталог целевых макетов и принципов их проверки.
- [`.memory-bank/sender-avatars/implementation-status.md`](.memory-bank/sender-avatars/implementation-status.md) — фактическое состояние этапов и блокировки.
- [`.memory-bank/sender-avatars/findings-and-fixes.md`](.memory-bank/sender-avatars/findings-and-fixes.md) — проверенные замечания N1-N9, решения и доказательства.

