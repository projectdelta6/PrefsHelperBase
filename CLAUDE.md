# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PrefsHelper is an Android library providing base classes for type-safe SharedPreferences and DataStore preference management. It supports storing various types including primitives, Date,
LocalDateTime, LocalDate, LocalTime, and Enums.

**Package namespace:** `com.duck.prefshelper`

## Build Commands

```bash
# Build the library
./gradlew :PrefsHelper:build

# Run all tests (all three test classes live in :app/src/test and run on the JVM —
# BaseDataStoreHelperTest runs under Robolectric, so no device/emulator is needed)
./gradlew :app:testDebugUnitTest

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.duck.app.BaseDataStoreHelperTest"

# Coverage (Kover; aggregates :PrefsHelper into :app since the tests live in :app).
# Android module -> variant-suffixed tasks. No device needed.
./gradlew :app:koverHtmlReportDebug   # HTML -> app/build/reports/kover/htmlDebug/index.html
./gradlew :app:koverVerifyDebug       # fails below the coverage floor (minBound in app/build.gradle.kts)

# Generate documentation (Dokka 2.x V2 task; the old `dokkaHtml` V1 task no longer exists)
./gradlew :PrefsHelper:dokkaGenerateHtml

# Clean build
./gradlew clean
```

## Architecture

The library provides two abstract base classes that consumers extend:

### BasePrefsHelper

- Wraps Android `SharedPreferences` with type-safe getters/setters
- Synchronous API for reads, async writes via `edit {}`
- Subclasses must provide `sharedPreferences` instance
- Takes a `dispatcher: CoroutineDispatcher = Dispatchers.IO` (no `Job`), used by `clearPrefs()`. Because the dispatcher carries no Job, `clearPrefs()` is cancelled when its caller is cancelled — this is deliberate, see the 2.0 migration note in the README.
- Preferred usage is the `*Pref` property delegate factories (e.g. `var flag by booleanPref(KEY, defaultValue = false)`). Each type has a non-nullable overload `(key, defaultValue)` and a nullable overload `(key)`. Temporal types (`Date`, `LocalDateTime`, `LocalDate`, `LocalTime`) are nullable-only and preserve the existing -1L sentinel.

### BaseDataStoreHelper

- Wraps Jetpack `DataStore<Preferences>` with type-safe methods
- Returns `Flow<T>` for reactive reads, plus blocking `readXxxValue()` methods (2s timeout)
- Both suspend and async write methods. The `writeXxxAsync` methods launch on `scope` and **return the `Job`**, so callers (and tests) can `.join()` to await completion instead of guessing with a delay. Delegate setters discard the `Job` (property setters return `Unit`), so they remain genuinely fire-and-forget.
- **Two constructors.** Primary takes `(dataStore: DataStore<Preferences>, dispatcher, scope)` — the injectable path, used by tests. Secondary convenience constructor takes `(context, preferenceName, dispatcher, scope)` and builds the store via `PreferenceDataStoreFactory.create(produceFile = { context.applicationContext.preferencesDataStoreFile(name) })`. Subclasses written against the old `(context, name)` signature are unchanged.
- `dispatcher` (no `Job`) is where `suspend` functions work; `scope` is where the `*Async` writes launch and is injectable so consumers can supply an app-lifetime scope with a `CoroutineExceptionHandler`. The default `scope` is per-instance — there is no longer a shared companion `SupervisorJob`.
- As with DataStore itself, only one live instance per backing file per process — keep subclasses singletons.
- Null values remove the key from storage
- Preferred usage is the `*Pref` delegate + `*PrefFlow` alias pair (e.g. `var userId by intPref(KEY, defaultValue = -1)` paired with `val userIdFlow = intPrefFlow(KEY, defaultValue = -1)`). Delegate setters route through the existing `*Async` writes so callers don't need to build their own `CoroutineScope`.

Both classes support: `String`, `Int`, `Long`, `Boolean`, `LocalDateTime`, `LocalDate`, `LocalTime`, `Enum<*>`. `BasePrefsHelper` additionally supports `Date`; `BaseDataStoreHelper` additionally supports `Double`.

## Gotchas

- **Inline reified factories that access `protected` members**: an `inline fun <reified T>` that returns an anonymous object (e.g. a `ReadWriteProperty`) calling `protected` methods on `BaseDataStoreHelper` throws `IllegalAccessError` at runtime — the anonymous class is emitted inside the *subclass* at inline time, losing JVM-level protected access. Fix: split into a thin `inline` + `reified` wrapper that forwards to a non-inline `@PublishedApi internal` helper which owns the anonymous object. See `enumPref` / `enumPrefInternal` in `BaseDataStoreHelper.kt`. `BasePrefsHelper` is unaffected because its get/set accessors are `public`.

- **Tests are JVM-only (Robolectric), not instrumented**: all three test classes live in `app/src/test` (`BasePrefsHelperTest`, `BaseDataStoreHelperTest`, and `BaseDataStoreHelperInjectionTest`). `BaseDataStoreHelperTest` uses a real `DataStore` but runs on the JVM via Robolectric (`@RunWith(AndroidJUnit4::class)` delegates to `RobolectricTestRunner` off-device). This is deliberate: **Kover cannot instrument on-device tests**, so DataStore coverage would read ~0% if the tests were instrumented — running them under Robolectric makes the `koverVerifyDebug` floor meaningful across both helpers. Robolectric's SDK is pinned to 36 in `app/src/test/resources/robolectric.properties` because `targetSdk = 37` has no Robolectric image yet.

## Project Structure

- `PrefsHelper/` - Library module (published to JitPack)
- `app/` - Sample/test application module

## Publishing

Library is published via JitPack. Version tags trigger releases automatically.
