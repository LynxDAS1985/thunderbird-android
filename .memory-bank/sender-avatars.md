# Аватары отправителей и проверенные логотипы

Статус: исследование и макет готовы, реализация в приложении ещё не начата.

Макеты:

- [`docs/design/sender-avatars-before-after.png`](../docs/design/sender-avatars-before-after.png) — первоначальное сравнение;
- [`docs/design/sender-avatar-settings-complete.png`](../docs/design/sender-avatar-settings-complete.png) — полный целевой экран настроек и итоговый список писем.

## Как работает сейчас

В списке писем `MessageItemContent()` запрашивает `ContactRepository.getPhotoUri()` по адресу отправителя. Если фото контакта найдено, создаётся `Avatar.Image`; иначе показывается `Avatar.Monogram`.

- `legacy/ui/legacy/src/main/java/com/fsck/k9/ui/messagelist/item/MessageItemContent.kt`
- `feature/mail/message/list/api/src/main/kotlin/net/thunderbird/feature/mail/message/list/ui/state/Avatar.kt`
- `feature/mail/message/list/internal/src/main/kotlin/net/thunderbird/feature/mail/message/list/internal/ui/component/MessageItemAvatar.kt`
- `core/android/common/src/main/kotlin/app/k9mail/core/android/common/contact/ContactRepository.kt`

В уведомлениях `SingleMessageNotificationCreator.setAvatar()` вызывает `K9NotificationResourceProvider.avatar()`. Сейчас provider создаёт `Recipient` только из адреса, без `photoThumbnailUri`, поэтому этот путь фактически получает буквенную заглушку, а не выполняет поиск фотографии контакта.

- `legacy/core/src/main/java/com/fsck/k9/notification/SingleMessageNotificationCreator.kt`
- `legacy/common/src/main/java/com/fsck/k9/notification/K9NotificationResourceProvider.kt`

## Где находятся настройки

Существующие переключатели находятся в `legacy/ui/legacy/src/main/res/xml/general_settings.xml`:

- `messagelist_show_contact_picture` — аватары в списке писем;
- `messagelist_colorize_missing_contact_pictures` — цвет буквенной заглушки;
- `notification_show_contact_picture` — аватары в уведомлениях.

Чтение и запись выполняет `GeneralSettingsDataStore.kt`. Модели и постоянное хранилище:

- `DisplayMessageListSettings.isShowContactPicture`;
- `NotificationPreference.isShowContactPictureInNotification`;
- `DefaultMessageListPreferencesManager`;
- `DefaultNotificationPreferenceManager`.

## Рекомендуемая точка внедрения

Не добавлять BIMI-запрос прямо в Compose-компонент. Создать единый `SenderAvatarResolver`, используемый списком писем и уведомлениями:

1. Фото точного адреса из контактов Android.
2. Проверенный BIMI-логотип домена отправителя.
3. Буквенная заглушка.

Результат должен содержать не только URI, но и происхождение и признак проверки, например `SenderAvatar.Image(uri, source, isVerified)` или `SenderAvatar.Monogram(value)`. Это позволит показывать зелёный знак только при настоящей проверке, а не для любого изображения.

Для списка заменить прямой вызов `contactRepository.getPhotoUri()` в `MessageItemContent.kt` и `MessageListFragment.loadMessages()` на результат resolver. Legacy-ветка без Compose проходит через `MessageViewHolder.setContactPicture()` и `ContactPictureLoader`, поэтому её тоже нужно подключить к общей политике. `MessageItemAvatar.kt` и `MessageItemAvatarCircle.kt` уже умеют загружать URL через `RemoteImage`; им нужно только добавить необязательный значок подтверждения.

Для уведомлений внедрить тот же resolver в `K9NotificationResourceProvider.avatar()` либо поднять его в `SingleMessageNotificationCreator.setAvatar()`, чтобы не было двух разных правил выбора картинки.

## Целевой экран настроек

Создать отдельный `PreferenceScreen` по пути `Настройки → Основные → Аватары отправителей`. Разместить его в корне `general_settings.xml` рядом с экранами `Вид` и `Уведомления`.

Перенести туда старые настройки `Фото контактов`, `Цветные места для фото` и `Показывать изображения контактов в уведомлениях`, сохранив их текущие значения. В старых разделах не оставлять вторые переключатели, чтобы два элемента не управляли одним полем.

Состав экрана:

- общий переключатель `Показывать аватары`;
- источник `Фото из контактов`;
- источник `Проверенные логотипы компаний`;
- выбор приоритета, если доступны оба изображения: `Фото из контактов` или `Логотип компании`;
- запасной вид: `Цветной круг с буквой` или `Стандартная иконка`;
- места показа: `В списке писем`, `В уведомлениях`, `В открытом письме`;
- `Значок подтверждения`, доступный только при включённых проверенных логотипах;
- `Загружать только по Wi-Fi`;
- команда `Очистить кэш логотипов`;
- живой предпросмотр результата.

Контакты и логотипы не являются взаимоисключающими источниками. Они работают вместе, а конфликт разрешает явная настройка приоритета. Если выбранный первым источник ничего не вернул, resolver автоматически пробует второй, затем показывает выбранную заглушку.

## Безопасность и приватность

- Не использовать favicon сайта как подтверждённый логотип: он не доказывает личность отправителя.
- Загружать BIMI только после проверки почтовой аутентификации конкретного письма и BIMI-записи домена. Заголовку `Authentication-Results` нельзя доверять без подтверждения, что его добавил доверенный почтовый сервер.
- Не показывать зелёный знак для фото из контактов или непроверенного изображения.
- Кэшировать результат по домену с ограниченным сроком, не по теме или содержимому письма.
- Дать пользователю отдельный выключатель сетевой загрузки логотипов.
- До появления доверенного источника результата DMARC/BIMI показывать логотип без зелёного знака либо не включать сетевой источник вообще.

## Минимальные тесты реализации

- приоритет: контакт выше BIMI, BIMI выше монограммы;
- отключение каждого источника в настройках;
- отсутствие значка при неуспешной проверке;
- одинаковый результат в списке и уведомлении;
- ошибка сети, неверный SVG, слишком большой ответ и истёкший кэш;
- миграция старых настроек без изменения текущего поведения.
