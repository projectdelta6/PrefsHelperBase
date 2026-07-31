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

Each type exposes a non-nullable delegate `*Pref(key, defaultValue)` and a nullable delegate `*Pref(key)`. `BaseDataStoreHelper` additionally exposes matching `*PrefFlow` accessors for reactive reads. The `java.time` types require API 26 (`@RequiresApi(O)`), as they did before.

The types match, but the two backends store and clear some of them differently — inherent to `SharedPreferences` versus `DataStore`, not an oversight:

| | `BasePrefsHelper` | `BaseDataStoreHelper` |
|---|---|---|
| Assigning `null` | Removes the key for the non-temporal types; writes a `-1L` sentinel for `Date`/`Instant`/`LocalDateTime`/`LocalDate`/`LocalTime` | Always removes the key |
| `Double` | Raw IEEE-754 bits in a `Long` — `SharedPreferences` has no double primitive, so never read that key back with `getLong` | Native `doublePreferencesKey` |
| `ByteArray` | Base64 (`NO_WRAP`) in a `String`; undecodable data reads back as null | Native `byteArrayPreferencesKey` |
| `Date` / `Instant` | Epoch millis, with `-1L` doubling as "absent" | Epoch millis in a `Long`; absent means absent |
| `Set<String>` | Defensive copy on both read and write, so neither side can be corrupted by later mutation | Immutable set from DataStore |

Two consequences worth knowing:

- **Pre-epoch timestamps.** `BasePrefsHelper` cannot distinguish `Date(-1L)` / `Instant.ofEpochMilli(-1)` from null; `BaseDataStoreHelper` can. If you store timestamps that could legitimately be 1ms before the epoch, use the DataStore helper.
- **`Set<Enum>` tolerates deleted constants.** Stored names that no longer match a constant are dropped on read rather than throwing, on both helpers — so removing an enum value doesn't break existing installs.

## Migrating to 2.0

2.0 splits the single `coroutineContext` constructor parameter, which was doing two unrelated jobs, into a `dispatcher` and a `scope`. There is no back-compat shim: deriving a dispatcher back out of an arbitrary `CoroutineContext` is exactly the fragile guesswork this change exists to remove.

**Most subclasses need no change at all.** If you extend `BasePrefsHelper()` or `BaseDataStoreHelper(context, "name")` without passing a coroutine context of your own, you are already on the new defaults.

**Two items on this list change behaviour without the compiler telling you: items 2 and 5.** The rest are removed or retyped symbols, so they fail the build and you'll find them immediately. Item 2 is the one that can corrupt state — read it properly. Item 5 only bites a specific threading arrangement.

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

## R8 / ProGuard / Minification

PrefsHelper is fully compatible with R8 (including full mode) and requires **no consumer ProGuard rules** (the shipped `consumer-rules.pro` is intentionally empty). Preference keys are always explicit string arguments you pass to the `*Pref(...)` delegates — they are **never** derived from Kotlin property names via reflection. R8 is free to rename, merge, and repackage your `BasePrefsHelper`/`BaseDataStoreHelper` subclasses and their properties without changing any persisted key. Enum values are stored by `Enum.name` (preserved by R8's default Android rules), so enum prefs survive obfuscation.

The one thing to keep in mind lives in *consumer* code, not the library: pass real string literals as keys. Since keys are explicit strings here, the classic prefs-library footgun (key derived from a renamed identifier) doesn't apply.
