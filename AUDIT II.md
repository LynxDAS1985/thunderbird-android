# Накопительный журнал аудита проекта (AUDIT II)

Правила ведения журнала определены в [AUDIT-REGISTRY.md](AUDIT-REGISTRY.md).
Файл ведётся строго в режиме **Append-Only**. Запрещено удалять или редактировать предыдущие записи.

---

## Запись аудита №1: 2026-10-19 — Комплексный аудит аватаров, BIMI и DMARC

- **Исполнитель / Модель:** Antigravity (Google DeepMind)
- **Роли проверяющих:** 3 независимых параллельных агента («Ломатель» Edge Cases, «Взгляд снаружи» Callers & Regressions, «Чистильщик» Rules & Architecture).
- **Ветка / Точка отсчёта:** `custom-brand-thunderbird-24.0` (коммит `9a9a10a` и выше).
- **Затронутые модули:** `legacy:core`, `app-common`, `core:preference`, `feature:mail:sender:avatar`, `legacy:ui:legacy`.

### Сводка выявленных и устранённых проблем:

| Код | Статус | Компонент | Суть проблемы и доказанное решение |
|---|---|---|---|
| **CRIT-01** 🔴 | Исправлено | `legacy/core`, `app-common` | Нарушение ADR-0009: `legacy:core` напрямую импортировал internal BIMI фабрику. Решение: зависимость `:internal` удалена из `legacy:core`, регистрация фабрики перенесена в `app-common` (`FeatureMailModule.kt`). |
| **CRIT-02** 🔴 | Исправлено | `legacy/core/preferences` | `Settings.VERSION` не был инкрементирован при добавлении `showContactPictureInNotification`. Решение: версия повышена до 112, написан `GeneralSettingsUpgraderTo112.java`. |
| **CRIT-03** 🔴 | Исправлено | `BimiDiskCache.kt` | Отсутствие синхронизации, `file.length()` падал при удалении, `Stream<Path>` concurrent modification. Решение: монитор `@Synchronized`, безопасный `safeFileSize()`, вызов `.toList()`. |
| **CRIT-04** 🔴 | Исправлено | `DefaultBimiLogoRepository.kt` | Блокирующий I/O в UI-потоке. Решение: `load()` переведён на корутины (`suspend fun withContext(ioDispatcher)`), добавлен неблокирующий синхронный метод `findCached(domain)` для списков. |
| **CRIT-05** 🔴 | Исправлено | `BimiRecordResolver.kt` | `NX_DOMAIN` давал `Failed` вместо `NotFound`, ломая 15-мин negative cache. Решение: явный маппинг в `BimiRecordLookupResult.NotFound`. |
| **CRIT-06** 🔴 | Исправлено | `BimiRecordResolver.kt` | Обрезались многострочные TXT (RFC 1035). Решение: объединение через `txtRecord.characterStrings?.joinToString("")`. |
| **CRIT-07** 🔴 | Исправлено | `BimiRecordResolver.kt` | Self-asserted BIMI с пустым `a=;` отклонялся. Решение: обновлен regex парсера. |
| **CRIT-08** 🔴 | Исправлено | `StrictSvgValidator.kt` | Блокировались градиенты SVG `url(#id)`. Решение: разрешены локальные якоря `#id`, строго запрещены внешние схемы. |
| **CRIT-09** 🔴 | Исправлено | `SafeBimiTransport.kt` | Невалидный URL редиректа приводил к `IllegalArgumentException`. Решение: безопасный `runCatching`. |
| **CRIT-10** 🔴 | Исправлено | `DefaultDmarcVerifier.kt` | Многострочный folding RFC 5322 и комментарии RFC 822 ломали разбор `authserv-id`. Решение: unfolding и очистка комментариев. |
| **CRIT-11** 🔴 | Исправлено | `DefaultDmarcVerifier.kt` | Не выставлялся метод `BIMI_ASSERTION`, из-за чего не показывался щит. Решение: добавление метода при валидном `bimi=pass`. |
| **CRIT-12** 🔴 | Исправлено | `MessageTopView`, `MessageHeader` | Отсутствовало сквозное подключение DMARC и BIMI к UI. Решение: добавлен `@+id/sender_verified_badge`, вызов verifier и repository. |
| **CRIT-13** 🔴 | Исправлено | `ContactPictureLoader.kt` | Иконка галочки `Icons.Outlined.Check` в заглушке контакта. Решение: замена на `Icons.Outlined.AccountCircle`. |
| **RISK-01..08** 🟡 | Исправлено | Разные | Устранены риски рассинхрона настроек (rollback), разделены настройки списка и письма, поддержан селектор `s=`, устранены риски утечки PII. |

### Доказательная база (фактические тесты):
- `GeneralSettingsDescriptionsTest`: BUILD SUCCESSFUL
- `:core:preference:impl:jvmTest`: 37 тестов PASS
- `:feature:mail:sender:avatar:internal:testDebugUnitTest`: 40 тестов PASS
- UI resolver tests (`MessageListSenderAvatarResolverTest`, `MessageViewSenderAvatarResolverTest`): PASS
- `spotlessCheck`: 45 задач UP-TO-DATE / SUCCESS
- `Check-Memory.ps1`: 0 ошибок.

