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

Subclass `BaseDataStoreHelper`, pass a `Context` and DataStore name. Use `*Pref` for imperative read/write and the matching `*PrefFlow` for reactive observation — the delegate setter dispatches to the library's internal scope so you don't need to open your own `CoroutineScope`.

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

Both `BasePrefsHelper` and `BaseDataStoreHelper` support `String`, `Int`, `Long`, `Boolean`, `LocalDateTime`, `LocalDate`, `LocalTime`, and `Enum<*>`. In addition:

- `BasePrefsHelper` supports `Date`.
- `BaseDataStoreHelper` supports `Double`.

Each type exposes a non-nullable delegate `*Pref(key, defaultValue)` and a nullable delegate `*Pref(key)` (assigning `null` clears the stored value on DataStore, or stores the sentinel on SharedPreferences for temporal types). `BaseDataStoreHelper` additionally exposes matching `*PrefFlow` accessors for reactive reads.
