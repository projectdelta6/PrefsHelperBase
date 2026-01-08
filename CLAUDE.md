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

### BaseDataStoreHelper

- Wraps Jetpack `DataStore<Preferences>` with type-safe methods
- Returns `Flow<T>` for reactive reads, plus blocking `readXxxValue()` methods (2s timeout)
- Both suspend and async (fire-and-forget via `scope.launch`) write methods
- Subclasses pass `Context` and preference name to constructor
- Null values remove the key from storage

Both classes support: `String`, `Int`, `Long`, `Double`, `Boolean`, `Date`, `LocalDateTime`, `LocalDate`, `LocalTime`, `Enum<*>`

## Project Structure

- `PrefsHelper/` - Library module (published to JitPack)
- `app/` - Sample/test application module

## Publishing

Library is published via JitPack. Version tags trigger releases automatically.
