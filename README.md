# PrefsHelperBase

Android library providing type-safe base classes for `SharedPreferences` and Jetpack `DataStore<Preferences>`.

[![Release](https://jitpack.io/v/projectdelta6/PrefsHelperBase.svg)](https://jitpack.io/#projectdelta6/PrefsHelperBase)

## Install

Add JitPack to your root `settings.gradle.kts` (or `build.gradle`):

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then in your module:

```kotlin
dependencies {
    implementation("com.github.projectdelta6:PrefsHelperBase:<version>")
}
```

`minSdk 21`. `datastore-preferences` is exposed as an `api` dependency, so `DataStore<Preferences>` is visible to you without declaring it yourself.

Upgrading from 1.x? See [Migrating to 2.0](#migrating-to-20) — it is a breaking release.

## Usage

### `BasePrefsHelper` (SharedPreferences)

Subclass `BasePrefsHelper`, provide a `SharedPreferences` instance, and expose preferences as property delegates. Reads are synchronous; writes are async via `edit { … }.apply()`.

```kotlin
class UserPrefs(context: Context) : BasePrefsHelper() {
    override val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    var username by stringPref(KEY_USERNAME, defaultValue = "")
    var isLoggedIn by booleanPref(KEY_IS_LOGGED_IN, defaultValue = false)

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
}
```

### `BaseDataStoreHelper` (DataStore<Preferences>)

Subclass `BaseDataStoreHelper`, pass a `Context` and DataStore name. Use `*Pref` for imperative read/write and the matching `*PrefFlow` for reactive observation — the delegate setter dispatches to the helper's `scope`, so you don't need to open your own `CoroutineScope`. That scope is injectable if you want to own it; see [Coroutines: `dispatcher` and `scope`](#coroutines-dispatcher-and-scope).

```kotlin
class AppPrefs(context: Context) : BaseDataStoreHelper(context, "app_prefs") {
    var userId by intPref(KEY_USER_ID, defaultValue = -1)
    val userIdFlow = intPrefFlow(KEY_USER_ID, defaultValue = -1)

    var theme by enumPref(KEY_THEME, default = Theme.SYSTEM)

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_THEME = "theme"
    }
}
```

### One instance per DataStore file — required

**`BaseDataStoreHelper` subclasses must be singletons.** DataStore permits only one live instance per file per process; construct a second one over the same file and it throws:

```
IllegalStateException: There are multiple DataStores active for the same file
```

This is a DataStore rule, not one this library adds, and it is easy to trip by accident — building a helper in `Activity.onCreate` is enough, because a configuration change constructs another one while the first is still alive.

`BasePrefsHelper` has no such constraint: `Context.getSharedPreferences` already returns a process-wide shared instance, so constructing several is wasteful but harmless.

**With a DI container**, register it as a singleton and you're done — this is the whole enforcement mechanism:

```kotlin
// Koin
single { AppPrefs(androidContext(), get(named("appScope"))) }   // correct
factory { AppPrefs(androidContext(), get(named("appScope"))) }  // throws on the second injection
```

```kotlin
// Hilt
@Provides @Singleton
fun provideAppPrefs(@ApplicationContext context: Context): AppPrefs = AppPrefs(context)
```

**Without a DI container**, you have to enforce it yourself. A Kotlin `object` works if you don't need a `Context` at construction; otherwise use a double-checked singleton and always pass the *application* context, never an Activity:

```kotlin
class AppPrefs private constructor(context: Context) : BaseDataStoreHelper(context, "app_prefs") {

    companion object {
        @Volatile private var INSTANCE: AppPrefs? = null

        fun getInstance(context: Context): AppPrefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPrefs(context.applicationContext).also { INSTANCE = it }
            }
    }
}
```

The sample app in `app/` shows the DI version, and its `PrefsHelper.kt` notes what the `getInstance()` it used to have was actually buying.

### Coroutines: `dispatcher` and `scope`

Both base classes take a `dispatcher`; `BaseDataStoreHelper` also takes a `scope`. They do two separate jobs and neither defaults will surprise you:

| Parameter | Used for | Default |
|-----------|----------|---------|
| `dispatcher` | Where `suspend` functions do their work (`withContext(dispatcher)`) | `Dispatchers.IO` |
| `scope` | Where fire-and-forget `*Async` writes (and the delegate setters) launch | Per-instance `CoroutineScope(dispatcher + SupervisorJob())` |

`dispatcher` deliberately carries no `Job`, so a `suspend` call like `clearPrefs()` is cancelled when its caller is cancelled — see the migration note below, this matters.

Inject `scope` when you want async write failures to go somewhere. A bare `SupervisorJob` swallows nothing but has no handler, so an exception from a fire-and-forget write reaches the thread's default handler and takes the process with it. Hand in an application-lifetime scope with a `CoroutineExceptionHandler` instead:

```kotlin
// Koin, but any DI container works the same way
single(named("appScope")) {
    CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
            Crashlytics.recordException(t)
        },
    )
}

single { AppPrefs(get(), get(named("appScope"))) }

class AppPrefs(
    context: Context,
    appScope: CoroutineScope,
) : BaseDataStoreHelper(context, "app_prefs", scope = appScope) {
    // …
}
```

Be clear about what that handler does and doesn't cover: `scope` owns the `*Async` writes and the delegate setters, so it sees their failures. It does **not** own DataStore's own internal actor, which has its own `SupervisorJob` — a corrupted-file or disk error surfacing from inside DataStore won't reach this handler. Injecting a scope is not "now I catch everything prefs-related".

### Injecting a `DataStore` for tests

`BaseDataStoreHelper`'s primary constructor takes the `DataStore<Preferences>` directly, so tests can supply one backed by a temp file and a test scheduler and drop the sleep-and-hope pattern entirely. Forward both constructors from your subclass:

```kotlin
class AppPrefs : BaseDataStoreHelper {
    constructor(context: Context) : super(context, "app_prefs")

    constructor(
        dataStore: DataStore<Preferences>,
        dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
    ) : super(dataStore, dispatcher, scope)

    var userId by intPref(KEY_USER_ID, defaultValue = -1)
    // …
}
```

Then, in a test:

```kotlin
@get:Rule val tempFolder = TemporaryFolder()
private var fileCount = 0

// Resolve the path up front and close over it — produceFile must yield the same
// file every time. Not tempFolder.newFile(...): that pre-creates the file and
// throws if it's ever invoked twice.
val file = File(tempFolder.root, "test_${fileCount++}.preferences_pb")

val store = PreferenceDataStoreFactory.create(
    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
    produceFile = { file },
)
val prefs = AppPrefs(store, StandardTestDispatcher(testScheduler), backgroundScope)
```

Give every test a fresh file — DataStore throws if two live instances point at the same one — and cancel the store's scope in teardown to release the file lock.

The library's own `BaseDataStoreHelperInjectionTest` is a worked example of this if you want something to copy.

### Composing a single façade

When an app stores preferences across several backing files (e.g. a per-user SharedPreferences plus a device-wide DataStore), it's convenient to wrap them in one façade so consumers only inject one type and cross-cutting operations (like "clear everything on logout") live in one place. Each property on the façade can re-expose a sub-helper's delegate via a Kotlin property reference (`by subHelper::property`):

```kotlin
class PrefsHelper(context: Context) {
    private val userPrefs by lazy { UserPrefs(context) }
    private val appPrefs by lazy { AppPrefs(context) }

    // Re-export sub-helper delegates; reads/writes pass straight through.
    var username by userPrefs::username
    var theme by appPrefs::theme
    val userIdFlow = appPrefs.userIdFlow

    suspend fun clearAll() {
        userPrefs.clearPrefs()
        appPrefs.clearPrefs()
    }
}
```

## Supported types

`BasePrefsHelper` and `BaseDataStoreHelper` support the same set of types as of 2.0:

`String`, `Int`, `Long`, `Float`, `Double`, `Boolean`, `ByteArray`, `Set<String>`, `Date`, `Instant`, `LocalDateTime`, `LocalDate`, `LocalTime`, `Enum<*>`, and `Set<Enum>`.

Most types expose a non-nullable delegate `*Pref(key, defaultValue)` and a nullable delegate `*Pref(key)`. `BaseDataStoreHelper` additionally exposes matching `*PrefFlow` accessors for reactive reads. The `java.time` types require API 26 (`@RequiresApi(O)`), as they did before.

The overload sets aren't identical across the two helpers — a historical shape rather than a rule:

| | `BasePrefsHelper` | `BaseDataStoreHelper` |
|---|---|---|
| `Date`, `Instant`, `LocalDateTime`, `LocalDate`, `LocalTime` | nullable only | nullable **and** non-null-with-default |
| `ByteArray` | nullable only | nullable only |
| `Set<Enum>` | non-null-with-default only | non-null-with-default only |
| everything else | both | both |

**Null means the same thing on both helpers as of 2.0:** assigning `null` removes the key, and an absent key reads back as `null`. There is no sentinel value any more — see [migration item 8](#8-the--1l-temporal-sentinel-is-gone) if you are upgrading.

What still differs is only the underlying encoding, which is inherent to `SharedPreferences` versus `DataStore`:

| | `BasePrefsHelper` | `BaseDataStoreHelper` |
|---|---|---|
| `Double` | Raw IEEE-754 bits in a `Long` — `SharedPreferences` has no double primitive, so never read that key back with `getLong` | Native `doublePreferencesKey` |
| `ByteArray` | Base64 (`NO_WRAP`) in a `String`; undecodable data reads back as null | Native `byteArrayPreferencesKey` |
| `Date` / `Instant` | Epoch millis in a `Long` | Epoch millis in a `Long` |
| `Set<String>` | Defensive copy on both read and write, so neither side can be corrupted by later mutation | Immutable set from DataStore |
| Migrations | [`migrateIfNeeded`](#versioned-migrations) — a stored version stamp | DataStore's own `DataMigration` list, passed to the constructor |

**`Set<Enum>` tolerates deleted constants** on both helpers: stored names that no longer match a constant are dropped on read rather than throwing, so removing an enum value doesn't break existing installs.

### Versioned migrations

`BasePrefsHelper.migrateIfNeeded` runs a block at most once per version, so a migration that *isn't* safe to repeat — "the stored value was seconds, it's millis now" — can be written without hand-rolling a guard:

```kotlin
class UserPrefs(context: Context) : BasePrefsHelper() {
    override val sharedPreferences =
        context.getSharedPreferences("user", Context.MODE_PRIVATE)

    init {
        migrateIfNeeded(currentVersion = 2) { from ->
            if (from < 2) migrateLegacyTemporalSentinels(KEY_DOB, KEY_LAST_SEEN)
        }
    }
}
```

An install with no stamp is treated as version 1 if the file already holds preferences, or as a fresh install if it's empty — so upgrades and first runs are told apart without you doing anything. Downgrades never rewind the stamp. The version lives under `BasePrefsHelper.KEY_HELPER_VERSION` in your own prefs file, so skip that key if you enumerate `getAll()`.

`BaseDataStoreHelper` deliberately has **no** equivalent. DataStore already ships `DataMigration`, which answers "have I run?" itself and is applied atomically before the first read rather than racing a stamp written afterwards. Pass migrations to the constructor and use the platform mechanism:

```kotlin
class AppPrefs(context: Context) : BaseDataStoreHelper(
    context,
    "app_prefs",
    migrations = listOf(SecondsToMillisMigration()),
)
```

## Migrating to 2.0

2.0 splits the single `coroutineContext` constructor parameter, which was doing two unrelated jobs, into a `dispatcher` and a `scope`. There is no back-compat shim: deriving a dispatcher back out of an arbitrary `CoroutineContext` is exactly the fragile guesswork this change exists to remove.

**Most subclasses need no change at all.** If you extend `BasePrefsHelper()` or `BaseDataStoreHelper(context, "name")` without passing a coroutine context of your own, you are already on the new defaults.

**Three items change behaviour without the compiler telling you: items 2, 5 and 8.** The rest are removed or retyped symbols, so they fail the build and you'll find them immediately.

- **Item 8** is the only one that changes how *existing stored data* is read. If you use temporal preferences on `BasePrefsHelper`, start there.
- **Item 2** can leave state half-written when a caller is cancelled.
- **Item 5** only bites one specific threading arrangement.

### 1. `coroutineContext` → `dispatcher` (+ optional `scope`)

```kotlin
// Before
class AppPrefs(context: Context) : BaseDataStoreHelper(
    context, "app_prefs", Dispatchers.IO + SupervisorJob() + handler,
)

// After
class AppPrefs(context: Context, appScope: CoroutineScope) : BaseDataStoreHelper(
    context, "app_prefs", dispatcher = Dispatchers.IO, scope = appScope,
)
```

Attaching a `CoroutineExceptionHandler` is now the `scope`'s job, not the dispatcher's — which is the point.

### 2. `suspend` functions are now caller-cancellable — audit your call sites

This is a real behaviour change, not a refactor. Previously `withContext(coroutineContext)` reparented the work onto a foreign `Job`, so `clearPrefs()` and the suspending writes behaved like `NonCancellable`: they finished even if the caller was cancelled. They no longer do.

The failure mode this creates is a partial logout. Given:

```kotlin
suspend fun onLogout() {
    userPrefs.clearPrefs()
    appPrefs.clearPrefs()
    apiClient.reset()
}
```

if that runs on a screen-scoped coroutine (`viewModelScope`, `screenModelScope`) and the user navigates away mid-call, you can now end up with SharedPreferences cleared and DataStore not. Fix it deliberately — either run logout on an application-lifetime scope, or wrap the body:

```kotlin
suspend fun onLogout() = withContext(NonCancellable) {
    // … the clears, as before
}
```

Anywhere else you relied on a prefs write surviving its caller's cancellation needs the same treatment.

### 3. The shared `supervisorJob` is gone

`BasePrefsHelper.Companion.supervisorJob` and `BaseDataStoreHelper.Companion.supervisorJob` have been deleted. They were `companion val`s — one `Job` shared by every helper instance in the process, so cancelling it once silently and permanently stopped async writes everywhere. The default `Job` is now created per instance. Nothing is expected to reference these, but grep for `supervisorJob` before upgrading.

### 4. The long-deprecated `*Bool` methods are gone

`BaseDataStoreHelper`'s four `protected` boolean methods, deprecated since before 1.x and carrying a "remove in a future release" note, have been deleted. Their replacements have been available the whole time:

| Removed | Replacement |
|---------|-------------|
| `writeBool(key, value: Boolean)` | `writeBoolean(key, value)` |
| `writeBool(key, value: Boolean?)` | `writeBoolean(key, value)` |
| `readNullableBool(key)` | `readBoolean(key)` |
| `readBool(key)` | `readBoolean(key, true)` |

Mind the last one: `readBool(key)` defaulted to **`true`**, not `false`, and `readBoolean(key)` on its own is the *nullable* overload returning `Flow<Boolean?>`. Pass the default explicitly — an IDE "replace with" that drops the `true` will silently change behaviour.

### 5. `readValueBlocking` no longer runs on `Dispatchers.IO`

DataStore is already main-safe, so the hop bought nothing and the `Job` it carried made the blocking read cancellable process-wide by accident. It now uses a plain `runBlocking`. One caveat: never call it from a thread the backing `DataStore`'s own scope is confined to — the read can't make progress and you'll block for the full 2-second timeout and get `null`.

### 6. New, not breaking: the `DataStore` is injectable

`BaseDataStoreHelper`'s primary constructor now takes a `DataStore<Preferences>` directly. Nothing forces you to use it — the `(context, preferenceName)` convenience constructor is unchanged, in signature *and* in scheduling — but it's what finally makes DataStore-backed subclasses unit-testable without sleeps. See [Injecting a `DataStore` for tests](#injecting-a-datastore-for-tests).

The convenience constructor deliberately keeps the store's own internal actor on `Dispatchers.IO` whatever `dispatcher` you pass, exactly as the old `preferencesDataStore` delegate did. Binding the store to a caller-supplied dispatcher would be a silent behaviour change, and a confined one (`Main`, single-threaded, a paused test dispatcher) would deadlock `readValueBlocking` against its own store. If you actually want to control where the store schedules, use the primary constructor.

### 7. New, not breaking: the two helpers now support the same types

2.0 closes the gap where each helper supported types the other didn't, and adds several new ones. `BasePrefsHelper` gains `Double`, `Float`, `ByteArray`, `Set<String>`, `Instant` and `Set<Enum>`; `BaseDataStoreHelper` gains `Date`, `Float`, `ByteArray`, `Set<String>`, `Instant` and `Set<Enum>`.

Nothing existing changes — these are additions, and each follows its helper's established conventions. See [Supported types](#supported-types) for the storage differences that remain between the two backends.

### 8. The `-1L` temporal sentinel is gone

**This one touches stored data. Read it if you use `Date`, `LocalDateTime`, `LocalDate` or `LocalTime` on `BasePrefsHelper`.** (`Instant` is new in 2.0, so no 1.x install ever wrote a sentinel for it.)

Before 2.0, assigning `null` to one of those wrote `-1L` rather than removing the key, and any stored `-1L` read back as `null`. 2.0 removes the key instead, matching `BaseDataStoreHelper`.

That fixes a real defect: epoch day `-1` is **1969-12-31**, so a `LocalDate` of that day was silently unstorable and read back as `null`. Same for `Date`/`Instant` at 1ms before the epoch.

But it also means **data written by 1.x now reads differently**. A key left at `-1L` by an older version is no longer "no value" — it is 1969-12-31. Sweep it once, at startup, before anything reads:

```kotlin
init {
    migrateIfNeeded(currentVersion = 2) { from ->
        if (from < 2) migrateLegacyTemporalSentinels(KEY_DOB, KEY_LAST_SEEN)
    }
}
```

`migrateLegacyTemporalSentinels` removes each listed key that currently holds `-1L`, so it reads as `null` exactly as before. It ignores absent keys, other values and non-`Long` keys, so it's safe to run repeatedly.

**List every temporal key the helper owns, including ones you rarely read.** The sweep is opt-in per key by design — it deliberately does *not* scan the file for `-1L` longs, because that would delete genuine `Long` preferences that happen to hold `-1`. The cost of that choice is that a key you forget stays broken, silently, and surfaces as a `1969-12-31` date rather than an error. Grep your subclass for every temporal delegate before writing the list.

Conversely, don't pass keys whose genuine value could be `-1L` — a real `Date(-1)` or `LocalDate` of 1969-12-31 written by 2.0 would be deleted.

Keep the call inside the `migrateIfNeeded(from < 2)` branch permanently rather than deleting it later: it then runs once per install and never touches data written by 2.0. A bare `init { migrateLegacyTemporalSentinels(…) }` is the form that stays dangerous, because it re-examines those keys on every construction forever.

`LocalTime` needs no migration: its valid range is 0..86399, so `-1` was never a legal value. Out-of-range stored values now read as `null` instead of throwing.

## R8 / ProGuard / Minification

PrefsHelper is fully compatible with R8 (including full mode) and requires **no consumer ProGuard rules** (the shipped `consumer-rules.pro` is intentionally empty). Preference keys are always explicit string arguments you pass to the `*Pref(...)` delegates — they are **never** derived from Kotlin property names via reflection. R8 is free to rename, merge, and repackage your `BasePrefsHelper`/`BaseDataStoreHelper` subclasses and their properties without changing any persisted key. Enum values are stored by `Enum.name` (preserved by R8's default Android rules), so enum prefs survive obfuscation.

The one thing to keep in mind lives in *consumer* code, not the library: pass real string literals as keys. Since keys are explicit strings here, the classic prefs-library footgun (key derived from a renamed identifier) doesn't apply.

### How that claim is verified

It isn't taken on trust. The sample app builds with `isMinifyEnabled = true`, so `./gradlew :app:assembleRelease` exercises the library through R8 — including its one reflective path, `Enum.name` matched against `enumConstants` — and produces **no `missing_rules.txt`**, which is R8's way of saying nothing needed keeping.

Build-time success only proves it links, though, so there is also an on-device test. `app/src/androidTest/.../R8SurvivalTest.kt` runs against the minified APK and round-trips enum, `Set<Enum>`, `Double`, `ByteArray`, `Set<String>` and `Date` preferences, plus the migration machinery:

```bash
./gradlew :app:connectedAndroidTest -PminifiedTests
```

**Manual, and deliberately not in CI** — it needs a connected device. Without `-PminifiedTests` the instrumented tests run against the unminified debug build and prove nothing, so the suite's first test asserts at runtime that its own classes were actually renamed and fails loudly if not.

Last verified on a real device with the sample's classes obfuscated to `NormalPrefs -> wn`, `Theme -> sw`, `Feature -> hh` — while `Theme.DARK` still persisted and reloaded as the string `"DARK"`. That is the property enum preferences depend on.

One caveat worth stating: running instrumented tests against a minified app needs harness keep rules of its own (`proguard-rules-instrumentation.pro`, applied only under `-PminifiedTests`) because R8 strips test-only entry points and any helper method the sample itself never calls. None of that is required by consumers, which is exactly why it lives in a separate conditional file rather than in `proguard-rules.pro`.

`gradle.properties` sets `android.r8.strictFullModeForKeepRules=false`, so the obvious question is whether the clean result depends on it. It doesn't — the release build and the full on-device suite were both re-run with it flipped to `true`, producing no missing rules and 7/7 passing. The repo keeps the original setting; the claim holds either way.
