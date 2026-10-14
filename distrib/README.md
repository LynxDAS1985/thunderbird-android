# Distrib

В этой папке хранится один локальный установочный файл:

- `thunderbird-full-debug.apk` — полный debug-вариант Thunderbird для проверки на Android.

APK создаётся командой `./gradlew :app-thunderbird:assembleFullDebug`, затем копируется сюда под постоянным именем. Сам APK исключён из Git из-за размера и создаётся заново из исходного кода. Это тестовая сборка с debug-подписью, не коммерческий release.

