# Проверки

Источник команд: [AGENTS.md](../AGENTS.md), [настройка окружения](../docs/contributing/development-environment.md), [тестирование](../docs/contributing/testing-guide.md).
Запускать из корня репозитория. Версия Java в `.java-version`: 21. Использовать Gradle Wrapper проекта.

```powershell
.\gradlew.bat spotlessCheck detekt lint test
.\gradlew.bat :app-thunderbird:assembleFullDebug
```

Для узкой задачи допустимы соответствующие проверки затронутого модуля; точные задачи брать из его конфигурации и CI. При исправлении ошибки полный минимум указан в `AGENTS.md`.
Команда набора тестов в `.github/workflows/build-android.yml`: `.\gradlew.bat testsOnCi --parallel`; она не тождественна общему `test`.
Для Markdown в `.github/workflows/quality-markdown.yml` предусмотрен `spotlessFlexmarkCheck`. Дополнительные проверки CI: `dependencyGuard` и `:quality:konsist:test`.
Android compileSdk задаётся в `build-plugin/src/main/kotlin/ThunderbirdProjectConfig.kt`; устанавливать SDK по этому файлу, а не по версии соседнего проекта.
Проверки на устройстве: `.\gradlew.bat connectedAndroidTest`; нужен телефон или эмулятор.

## Состояние на 2026-10-09

Команда `.\gradlew.bat spotlessCheck detekt lint test` выполнена как попытка запуска, но завершилась с ошибкой до выполнения задач: `JAVA_HOME is not set and no 'java' command could be found in your PATH`.
В проверенных стандартных папках JDK, Android Studio JBR и Android SDK окружение не найдено. Это не доказательство отсутствия во всех каталогах компьютера.
Linter и Unit-tests НЕ прошли и фактически не выполнялись. APK не собран, тесты на устройстве не запускались.
Следующий шаг: настроить JDK 21, Android SDK по конфигурации проекта и повторить команды.

Для текущей документации отдельно проверить: наличие всех файлов, регистрацию в обеих картах, относительные ссылки и `git diff --check`. Эти проверки не подтверждают работоспособность приложения.
Результат проверки структуры 2026-10-09: 15 файлов памяти зарегистрированы в обеих картах, 38 относительных ссылок ведут к существующим файлам, пустых документов нет. `git diff --check` для отслеживаемого изменения успешен.

## Инструменты ведения проекта

Проверки конкретного изменения выбирать по [verification-matrix.md](verification-matrix.md). Исторические числа выше относятся к первой подготовке памяти, а не к её текущему объёму.

```powershell
powershell -NoProfile -File scripts/project/Check-Memory.ps1
powershell -NoProfile -File scripts/project/tests/Test-Memory.ps1
powershell -NoProfile -File scripts/project/Test-Environment.ps1 -Json
powershell -NoProfile -File scripts/project/tests/Test-Environment.Tests.ps1
```

Команды выполнять из корня репозитория. Ненулевой код диагностики окружения означает блокировку подготовки Android-сборки; успешные тесты самого диагностического скрипта не означают наличие Java/SDK на компьютере.
Повторная попытка `spotlessFlexmarkCheck` в сессии улучшений также остановилась из-за недоступной Java до выполнения задач. Не считать проверку памяти заменой Markdown-форматтеру CI.

## Состояние на 2026-10-10

Переносимый Temurin JDK 21 находится в `C:\Projects\Mail\.tools\temurin21`, Android SDK — в `C:\Projects\Mail\.tools\android-sdk`; пути не входят в Git. Локальный `local.properties` задаёт `sdk.dir` и игнорируется Git.

Успешно выполнены:

- `:core:featureflag:jvmTest`;
- `:core:preference:api:jvmTest`;
- `:core:preference:impl:jvmTest`, включая все 8 сочетаний прежних настроек аватаров;
- профильные `spotlessCheck` для feature flags, preference и legacy-модулей;
- `:legacy:ui:legacy:assembleDebug` — 730 задач;
- `:app-thunderbird:assembleDebug` — оба варианта, 2248 задач, 29 минут 38 секунд.

Последняя команда доказала собираемость обоих вариантов, но дальше использовать `:app-thunderbird:assembleFullDebug` по решению [D-006](decisions.md). Не выполнены `connectedAndroidTest`, ручная установка и обновление поверх старого APK: телефон или эмулятор не подключён. Полный общий набор `spotlessCheck detekt lint test` также не запускался целиком.
