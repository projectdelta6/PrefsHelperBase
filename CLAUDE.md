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

# R8 verification. The sample's release build is minified on purpose, so this proves the
# library needs no consumer keep rules (no missing_rules.txt = nothing needed keeping).
./gradlew :app:assembleRelease

# Behavioural R8 check — MANUAL, needs a connected device, deliberately not in CI.
# Points instrumented tests at the minified release build instead of debug.
./gradlew :app:connectedAndroidTest -PminifiedTests

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
- Preferred usage is the `*Pref` property delegate factories (e.g. `var flag by booleanPref(KEY, defaultValue = false)`). Most types have a non-nullable overload `(key, defaultValue)` and a nullable overload `(key)`; the temporal types (`Date`, `Instant`, `LocalDateTime`, `LocalDate`, `LocalTime`) and `ByteArray` are nullable-only here, and `enumSetPref` is non-null-with-default only. The `-1L` sentinel those temporal types used is **gone as of 2.0** — null removes the key, see the Migrations section below.

### BaseDataStoreHelper

- Wraps Jetpack `DataStore<Preferences>` with type-safe methods
- Returns `Flow<T>` for reactive reads, plus blocking `readXxxValue()` methods (2s timeout)
- Both suspend and async write methods. The `writeXxxAsync` methods launch on `scope` and **return the `Job`**, so callers (and tests) can `.join()` to await completion instead of guessing with a delay. Delegate setters discard the `Job` (property setters return `Unit`), so they remain genuinely fire-and-forget.
- **Two constructors.** Primary takes `(dataStore: DataStore<Preferences>, dispatcher, scope)` — the injectable path, used by tests. Secondary convenience constructor takes `(context, preferenceName, dispatcher, scope)` and builds the store via `PreferenceDataStoreFactory.create(produceFile = { context.applicationContext.preferencesDataStoreFile(name) })`. Subclasses written against the old `(context, name)` signature are unchanged.
- `dispatcher` (no `Job`) is where `suspend` functions work; `scope` is where the `*Async` writes launch and is injectable so consumers can supply an app-lifetime scope with a `CoroutineExceptionHandler`. The default `scope` is per-instance — there is no longer a shared companion `SupervisorJob`.
- As with DataStore itself, only one live instance per backing file per process — keep subclasses singletons.
- Null values remove the key from storage
- Preferred usage is the `*Pref` delegate + `*PrefFlow` alias pair (e.g. `var userId by intPref(KEY, defaultValue = -1)` paired with `val userIdFlow = intPrefFlow(KEY, defaultValue = -1)`). Delegate setters route through the existing `*Async` writes so callers don't need to build their own `CoroutineScope`.

Both classes support the same types as of 2.0: `String`, `Int`, `Long`, `Float`, `Double`, `Boolean`, `ByteArray`, `Set<String>`, `Date`, `Instant`, `LocalDateTime`, `LocalDate`, `LocalTime`, `Enum<*>`, `Set<Enum>`.

Storage conventions still differ by backend, and deliberately so:
- **`Double` on `BasePrefsHelper`** is stored as raw IEEE-754 bits via `putLong`/`Double.fromBits`, because `SharedPreferences` has no double primitive. Reading that key with `getLong` returns the bit pattern, not the number.
- **`ByteArray` on `BasePrefsHelper`** is Base64 (`NO_WRAP`) in a String, since SharedPreferences has no binary type. Undecodable data logs and returns null rather than throwing.
- **`Set<String>` on `BasePrefsHelper`** copies on both read and write. `SharedPreferences.getStringSet` documents its result as one callers must not modify, and the platform keeps a reference to the set it is handed — both directions are trapped, so both are copied.
- **Null semantics are identical on both helpers** as of 2.0: assigning null removes the key, an absent key reads as null. The `-1L` temporal sentinel `BasePrefsHelper` used before 2.0 is gone — it made `LocalDate` 1969-12-31 (epoch day -1) unstorable. `migrateLegacyTemporalSentinels(vararg keys)` sweeps stale sentinels left by 1.x; `getLocalTime` also treats out-of-range values as null, since `LocalTime.ofSecondOfDay(-1)` throws.
- **`Set<Enum>`** is stored as a set of `Enum.name` on both. Unknown names are dropped on read so deleting an enum constant doesn't break existing installs.

## Migrations

`BasePrefsHelper.migrateIfNeeded(currentVersion) { from -> }` runs a block at most once per version, stamped under `KEY_HELPER_VERSION` in the consumer's own prefs file. An unstamped file is treated as `VERSION_LEGACY` (1) if it holds anything, or as a fresh install if empty — that's how pre-versioning installs are told from new ones. Downgrades don't rewind the stamp. The stamp is written with `commit`, so the first call does a small synchronous write.

`BaseDataStoreHelper` has **no** equivalent, deliberately: DataStore's own `DataMigration` already tracks whether it has run and is applied atomically before the first read, which is strictly better than stamping a version afterwards. The convenience constructor takes a `migrations` list that is passed straight to `PreferenceDataStoreFactory`. Don't add a parallel version-key scheme here.

## Gotchas

- **Inline reified factories that access `protected` members**: an `inline fun <reified T>` that returns an anonymous object (e.g. a `ReadWriteProperty`) calling `protected` methods on `BaseDataStoreHelper` throws `IllegalAccessError` at runtime — the anonymous class is emitted inside the *subclass* at inline time, losing JVM-level protected access. Fix: split into a thin `inline` + `reified` wrapper that forwards to a non-inline `@PublishedApi internal` helper which owns the anonymous object. See `enumPref` / `enumPrefInternal` and `enumSetPref` / `enumSetPrefInternal` in `BaseDataStoreHelper.kt`. `BasePrefsHelper` is unaffected because its get/set accessors are `public`.

This only reproduces when the delegate is used **from a subclass in another module**, which is why `BaseDataStoreHelperTest`'s `TestDataStoreHelper` (in `:app`) declares `delegate`-prefixed properties for both enum delegates. A test that calls `readEnumSetValue` directly will not catch a regression here.

- **R8 verification is manual and needs a device**: `app/src/androidTest/R8SurvivalTest.kt` is the only test that can check the README's "no consumer ProGuard rules" claim, since unit tests never run R8 and Robolectric runs unminified code. It only means anything with `-PminifiedTests`, which flips `testBuildType` to the minified release build; its first test asserts at runtime that classes really were renamed so a debug run can't pass vacuously. Harness keeps live in `proguard-rules-instrumentation.pro`, applied **only** under that flag — keep them out of `proguard-rules.pro`, or a plain `assembleRelease` stops being evidence that consumers need no rules.

- **Tests are JVM-only (Robolectric), not instrumented**: all *unit* test classes live in `app/src/test` (`BasePrefsHelperTest`, `BaseDataStoreHelperTest`, and `BaseDataStoreHelperInjectionTest`). `BaseDataStoreHelperTest` uses a real `DataStore` but runs on the JVM via Robolectric (`@RunWith(AndroidJUnit4::class)` delegates to `RobolectricTestRunner` off-device). This is deliberate: **Kover cannot instrument on-device tests**, so DataStore coverage would read ~0% if the tests were instrumented — running them under Robolectric makes the `koverVerifyDebug` floor meaningful across both helpers. Robolectric's SDK is pinned to 36 in `app/src/test/resources/robolectric.properties` because `targetSdk = 37` has no Robolectric image yet.

## Project Structure

- `PrefsHelper/` - Library module (published to JitPack)
- `app/` - Sample/test application module

## Publishing

Library is published via JitPack. Version tags trigger releases automatically.
