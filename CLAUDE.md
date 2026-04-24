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

# Run unit tests
./gradlew :PrefsHelper:test

# Run instrumented tests (requires connected device/emulator)
./gradlew :PrefsHelper:connectedAndroidTest

# Run a single instrumented test class (--tests is rejected by connectedAndroidTest)
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.duck.app.BaseDataStoreHelperTest

# Generate documentation
./gradlew :PrefsHelper:dokkaHtml

# Clean build
./gradlew clean
```

## Architecture

The library provides two abstract base classes that consumers extend:

### BasePrefsHelper

- Wraps Android `SharedPreferences` with type-safe getters/setters
- Synchronous API for reads, async writes via `edit {}`
- Subclasses must provide `sharedPreferences` instance
- Uses coroutine context (defaults to `Dispatchers.IO + SupervisorJob`) for `clearPrefs()`
- Preferred usage is the `*Pref` property delegate factories (e.g. `var flag by booleanPref(KEY, defaultValue = false)`). Each type has a non-nullable overload `(key, defaultValue)` and a nullable overload `(key)`. Temporal types (`Date`, `LocalDateTime`, `LocalDate`, `LocalTime`) are nullable-only and preserve the existing -1L sentinel.

### BaseDataStoreHelper

- Wraps Jetpack `DataStore<Preferences>` with type-safe methods
- Returns `Flow<T>` for reactive reads, plus blocking `readXxxValue()` methods (2s timeout)
- Both suspend and async (fire-and-forget via `scope.launch`) write methods
- Subclasses pass `Context` and preference name to constructor
- Null values remove the key from storage
- Preferred usage is the `*Pref` delegate + `*PrefFlow` alias pair (e.g. `var userId by intPref(KEY, defaultValue = -1)` paired with `val userIdFlow = intPrefFlow(KEY, defaultValue = -1)`). Delegate setters route through the existing `*Async` writes so callers don't need to build their own `CoroutineScope`.

Both classes support: `String`, `Int`, `Long`, `Boolean`, `LocalDateTime`, `LocalDate`, `LocalTime`, `Enum<*>`. `BasePrefsHelper` additionally supports `Date`; `BaseDataStoreHelper` additionally supports `Double`.

## Gotchas

- **Inline reified factories that access `protected` members**: an `inline fun <reified T>` that returns an anonymous object (e.g. a `ReadWriteProperty`) calling `protected` methods on `BaseDataStoreHelper` throws `IllegalAccessError` at runtime — the anonymous class is emitted inside the *subclass* at inline time, losing JVM-level protected access. Fix: split into a thin `inline` + `reified` wrapper that forwards to a non-inline `@PublishedApi internal` helper which owns the anonymous object. See `enumPref` / `enumPrefInternal` in `BaseDataStoreHelper.kt`. `BasePrefsHelper` is unaffected because its get/set accessors are `public`.

## Project Structure

- `PrefsHelper/` - Library module (published to JitPack)
- `app/` - Sample/test application module

## Publishing

Library is published via JitPack. Version tags trigger releases automatically.
