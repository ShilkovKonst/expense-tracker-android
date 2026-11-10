# SpendObserver — Android

An expense and income tracker. Fully local: no server, no account and no
network; all data is stored in an SQLite database on the device.

Ready-to-install builds (`.apk`) are on the
[Releases](https://github.com/ShilkovKonst/expense-tracker-android/releases)
page. Requires Android 11 or later.

## Features

- Several independent trackers, each with its own tags and records.
- Records grouped by year and month, with totals at every level.
- A record can be split into parts with their own amounts and tags (a store
  receipt, for example); the remainder up to the receipt total is calculated
  automatically.
- Two levels of tags: global ones (where, with whom) go on the record, regular
  ones (what for) go on its parts.
- A calculator in the amount fields: `+ − × ÷` and parentheses.
- Per-tracker statistics with charts: all time, a year or a month.
- Data export and import as JSON.
- Light, dark and system theme.
- Languages: English, Русский, Français, Deutsch.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Storage | Room (SQLite) |
| Async | Kotlin Coroutines + Flow |
| Architecture | MVVM + Repository, manual DI (`AppContainer`) |
| Navigation | Navigation Compose (type-safe routes) |
| Charts | Vico |
| Tests | JUnit 4, Room and Compose UI Test (instrumented) |

## Building

```
./gradlew assembleDebug      # debug build
./gradlew test               # JVM tests
./gradlew connectedCheck     # instrumented tests (needs an emulator or a device)
```

Release builds are signed with a local key. Its settings are read from
`keystore.properties` in the project root (listed in `.gitignore`):

```properties
storeFile=<path to .jks>
storePassword=…
keyAlias=…
keyPassword=…
```

Without that file the debug build and the tests work as usual; only the
release build is left unsigned.
