# Тайный Санта

Android-приложение для жеребьёвки подарков: создайте комнату, раздайте код друзьям, проведите жеребьёвку, и каждый увидит только того, кому дарит.

**[Скачать последний APK](https://github.com/MFrog99/secret-santa-android/releases/latest/download/app-release.apk)** (Android 8.0 и новее)

## Возможности

- Регистрация и вход по email и паролю, сессия сохраняется между запусками.
- Комнаты с 6-символьным кодом, вход по коду, пока жеребьёвка не проведена.
- Участники и статус комнаты обновляются в реальном времени.
- Пожелания к подарку у каждого участника.
- Жеребьёвка без самоподарков. Владелец может в конце раскрыть всех Сант.

## Как устроена анонимность

Жеребьёвка выполняется на сервере функцией Postgres `draw_room`, а не на телефоне владельца, поэтому пары не знает никто, включая владельца. Row Level Security отдаёт пользователю только его собственную запись `assignments`. Запись о его Санте становится видна после `reveal_room`.

## Стек

Kotlin, Jetpack Compose (Material 3), MVVM + Repository, корутины и Flow, [supabase-kt](https://github.com/supabase-community/supabase-kt). Бэкенд: Supabase (Auth, PostgreSQL, Realtime, RLS). Сборка и публикация: GitHub Actions и GitHub Releases.

## Сборка

Нужны JDK 17 и Android SDK. Создайте `local.properties` (файл не попадает в git):

```properties
SUPABASE_URL=https\://<project-ref>.supabase.co
SUPABASE_ANON_KEY=<anon key>
```

```bash
./gradlew testDebugUnitTest assembleRelease
```

Схема базы лежит в `supabase/migrations`, проверки бэкенда в `supabase/tests/backend_checks.sql`. Они выполняются в транзакции с откатом и ничего не оставляют в базе:

```bash
supabase db push --remote
```

```bash
supabase db query --remote -f supabase/tests/backend_checks.sql
```

## CI/CD

Каждый пуш в `main` запускает тесты и сборку, затем публикует APK в релиз `v<appVersionName>`. Новая версия выпускается увеличением `appVersionName` в `gradle.properties`.

Секреты репозитория: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `DEBUG_KEYSTORE_B64`.

Anon key по замыслу Supabase является публичным и извлекается из любого APK. Данные защищает RLS, а не секретность этого ключа.
