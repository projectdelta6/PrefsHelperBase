# Coroutine API redesign — injectable scope + correct cancellation

**Status:** implemented on `feature/coroutine-api-redesign`, 2026-07-31 — see [Outcome](#outcome)
**Drafted:** 2026-07-30
**Provenance:** came out of a main-thread-safety audit of the repo layer in
`AIM-Capture-Android`. Line references below were verified against the working tree on that date —
re-check them if the files have moved on. Design was reviewed by a second opinion; the two "gotcha"
sections exist because that review found problems with the first draft.

---

## Outcome

Built as **scope + injectable DataStore** — the wider of the two options in
[The testability catch](#the-testability-catch), so goal 2 is genuinely delivered rather than
deferred. Everything below is the original proposal, kept as the design record. Where the
implementation deviates from it:

| Proposal said | Built instead | Why |
|---|---|---|
| `dataStore: DataStore<Preferences>? = null` param alongside `context` | **Two constructors** — primary `(dataStore, dispatcher, scope)`, secondary `(context, preferenceName, dispatcher, scope)` | `context` is used for nothing else in the class, so requiring it on the injected path is dead weight. Existing `super(context, "name")` subclasses stay source-compatible. |
| Keep the `Context.dataStoreInstance` extension delegate | Deleted; the convenience constructor uses `PreferenceDataStoreFactory.create(produceFile = { context.applicationContext.preferencesDataStoreFile(name) })` | The delegate's singleton guard was per *helper instance*, not process-wide, so it was never buying the protection it looked like it was. `SingleProcessDataStore`'s own file-lock check still catches duplicate instances. |
| `@PublishedApi internal val dispatcher` on **both** classes | `@PublishedApi internal` on `BaseDataStoreHelper`, plain `protected` on `BasePrefsHelper` | No `inline` function in `BasePrefsHelper` touches it, so `protected` is both sufficient and the smaller diff from the status quo. |

Two things the proposal flagged that turned out fine: the existing 1,621-line test suite passes
**unchanged** against the new API, and `scope` moving from a body property to a constructor property
does not reintroduce the `IllegalAccessError` gotcha (that only bites members captured inside an
anonymous object emitted by an inline function — a direct `this.scope` read in an inlined
`protected inline` body is legal).

Still outstanding, and deliberately out of this repo: the AIM app's logout call site (below), and
the version bump + JitPack tag.

---

## Goals

1. **Injectable scope.** Consumers should be able to hand in a `CoroutineScope` they own — e.g. an
   application-lifetime scope from Koin with a `CoroutineExceptionHandler` — so that fire-and-forget
   prefs writes report failures instead of crashing the process.
2. **Testability.** Subclasses should be unit-testable with deterministic coroutines.

Goal 1 is achievable with the change below. **Goal 2 is not, without an extra step** — see
[The testability catch](#the-testability-catch). Read that before estimating.

---

## Diagnosis

One constructor parameter is doing two unrelated jobs:

```kotlin
// BasePrefsHelper.kt:36-37   and   BaseDataStoreHelper.kt:52-55
coroutineContext: CoroutineContext = Dispatchers.IO + supervisorJob
```

It simultaneously decides:

- **(a)** which dispatcher `suspend` functions hop to — `withContext(coroutineContext)`, at
  `BasePrefsHelper.kt:47` and `BaseDataStoreHelper.kt:108`, `:159`
- **(b)** which Job owns fire-and-forget launches — `BaseDataStoreHelper.kt:74`,
  `protected val scope = CoroutineScope(coroutineContext)`, used by the `*Async` helpers at `:129`,
  `:148`, `:173`

Conflating them causes three concrete problems.

### 1. `suspend` functions ignore their caller's cancellation

`withContext(context)` where `context` carries a Job **reparents** the block onto that Job instead of
the caller's. So `clearPrefs()` and `writeValue()` behave like `NonCancellable` with respect to
whoever called them. A `suspend` function should be cancellable by its caller; this one isn't.

A `suspend` function should only ever be handed a **dispatcher** — no Job.

### 2. The default `SupervisorJob` is shared process-wide

```kotlin
// BasePrefsHelper.kt:461  and  BaseDataStoreHelper.kt:1184  (companion objects)
val supervisorJob = SupervisorJob()
```

A companion `val` means **one Job shared across every instance of that class in the process**. Cancel
it once and every prefs helper in the app silently stops doing async work, permanently, with no
error. Nothing cancels it today, so it currently behaves as a harmless keep-alive parent — but it is
a live footgun, and it makes the blocking reads at `:212` cancellable process-wide by accident.

### 3. There is no way to inject a scope

Which is the thing that prompted all this. A consumer can pass a context, but then it also
(unavoidably) changes where `suspend` functions dispatch, and it still can't attach an exception
handler to the launches without also reparenting the suspend calls.

---

## Proposed API

Split the parameter in two.

```kotlin
abstract class BaseDataStoreHelper(
	context: Context,
	preferenceName: String,
	@PublishedApi internal val dispatcher: CoroutineDispatcher = Dispatchers.IO,
	protected val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob()),
) {
```

```kotlin
abstract class BasePrefsHelper(
	@PublishedApi internal val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
```

- `dispatcher` — used by `suspend` functions via `withContext(dispatcher)`. No Job, so caller
  cancellation propagates correctly.
- `scope` — used for fire-and-forget launches. Injectable; this is the goal-1 payload.
- The default `SupervisorJob()` becomes **per-instance**, replacing the shared companion one.
- A default parameter referencing an earlier parameter (`scope` defaulting to an expression over
  `dispatcher`) is legal Kotlin and compiles.

`BasePrefsHelper` has no launches and no `scope` today, so it only needs `dispatcher`. Adding a
`scope` there too is optional — do it only if you want symmetry, not because anything needs it.

### Visibility constraint

`dispatcher` is touched by `protected inline` functions (`writeValue` at `:159`, the
`readValueBlocking` family at `:211`/`:225`/`:235`). Keeping it `@PublishedApi internal` — matching
today's `coroutineContext` — is the safe option. `protected` would also satisfy a `protected inline`
caller, but `@PublishedApi internal` is a smaller diff from the status quo. `scope` stays
`protected` and already works from the `protected inline` `*Async` helpers.

---

## Change list

### `BaseDataStoreHelper.kt`

| Line | Now | Becomes |
|------|-----|---------|
| 52-55 | `coroutineContext: CoroutineContext = Dispatchers.IO + supervisorJob` | `dispatcher` + `scope` params as above |
| 74 | `protected val scope = CoroutineScope(coroutineContext)` | delete — `scope` is now a constructor property |
| 108 | `clearPrefs() = withContext(coroutineContext)` | `withContext(dispatcher)` |
| 159 | `writeValue(...) = withContext(coroutineContext)` | `withContext(dispatcher)` |
| 212 | `runBlocking(coroutineContext) { withTimeoutOrNull(2.seconds) { … } }` | `runBlocking { withTimeoutOrNull(2.seconds) { … } }` — drop the context entirely |
| 1184 | companion `val supervisorJob = SupervisorJob()` | delete (after checking for consumer references) |

On `:212` specifically: DataStore is already main-safe, so the IO dispatch buys nothing, and passing
a Job into `runBlocking` is the footgun described above.

### `BasePrefsHelper.kt`

| Line | Now | Becomes |
|------|-----|---------|
| 36-37 | `coroutineContext: CoroutineContext = Dispatchers.IO + supervisorJob` | `dispatcher: CoroutineDispatcher = Dispatchers.IO` |
| 47 | `clearPrefs() = withContext(coroutineContext)` | `withContext(dispatcher)` |
| 461 | companion `val supervisorJob = SupervisorJob()` | delete (after checking for consumer references) |

Also update the class KDoc on both — it currently documents the `coroutineContext` parameter and the
shared-`supervisorJob` default.

---

## The testability catch

**Injecting a scope does not, on its own, make DataStore-backed subclasses unit-testable.**

`preferencesDataStore` creates its **own** internal `Dispatchers.IO` scope and performs real file
I/O. An injected `TestScope` with virtual time cannot make `dataStore.edit { }` deterministic,
because the work doesn't run on the scope we injected. So the change above delivers goal 1 and
quietly fails goal 2.

To actually get goal 2, the **`DataStore` instance itself must be injectable**: either a constructor
parameter defaulting to the current delegate, or constructed via
`PreferenceDataStoreFactory.create(scope = …)` so tests can supply their own.

**Decision required.** Two options:

- **Scope-only (recommended first pass).** Smaller, contained, delivers the error-handling and
  Koin-ownership win. Be explicit that unit tests aren't arriving in this round.
- **Scope + injectable DataStore.** Delivers real tests, but it is a wider API change and a bigger
  piece of work. Reasonable as a follow-up once the split above has landed.

`BasePrefsHelper` (SharedPreferences) does not have this problem — it's synchronous and already
testable with a fake `SharedPreferences`.

---

## Consumer impact

### Known consumers

- `AIM-Capture-Android` — `NormalPrefs`, `DevicePrefs` (both `BasePrefsHelper`), `NormalDataStore`
  (`BaseDataStoreHelper`), and `CameraDataStore` in the `:insta360Helper` module.
- Other Appoly apps that depend on `PrefsHelperBase` — enumerate before releasing.

Most subclasses **don't pass the parameter at all** and rely on the default, so they are unaffected
by the signature change. Only call sites that explicitly pass `coroutineContext` need editing.

### Required change at the logout call site — do not skip this

Fixing problem 1 (making `suspend` functions properly caller-cancellable) has a real consequence.
`PrefsHelper.onLogout()` in the AIM app runs four sequential clears:

```kotlin
suspend fun onLogout() {
	normalPrefs.clearPrefs()
	normalDataStore.clearPrefs()
	RetrofitClient.resetClients()
	cameraDataStore.clearPrefs()
}
```

Today the foreign-Job `withContext` makes those effectively uncancellable, which is accidentally why
logout always completes. Its caller is `SettingsScreenModel.logout()` running on `screenModelScope`,
which **is cancelled when the settings screen is disposed**. After this change, navigating away
mid-logout could leave SharedPreferences cleared but DataStore and the Retrofit clients not — a
partially logged-out state.

**Fix it in the same release:** run logout on an application-lifetime scope (the AIM app now has one
in `appModule` as `named("appScope")`), or wrap the body in `withContext(NonCancellable)`. Pick one
deliberately — this is a genuine behaviour change, not a refactor.

### Direct `supervisorJob` references

`grep` all consumers for `supervisorJob` before deleting the companion properties. Nothing is
expected to reference them, but confirm rather than assume.

---

## Release strategy

**Major version bump with a migration note. No deprecated back-compat shim.**

A secondary constructor preserving the old `coroutineContext` parameter would have to derive a
dispatcher back out of a `CoroutineContext` — exactly the fragile extraction this split exists to
avoid — and you own every known consumer, so the compatibility burden isn't worth carrying.

The migration note should cover:

1. `coroutineContext` → `dispatcher` (+ optional `scope`).
2. `suspend` functions are now caller-cancellable — audit anywhere you relied on a prefs write
   surviving the caller's cancellation, and see the logout note above.
3. The shared `supervisorJob` is gone; the default Job is now per-instance.

---

## Checklist

- [x] Decide scope-only vs scope + injectable DataStore — chose **scope + injectable DataStore**
- [x] `BaseDataStoreHelper`: split constructor, delete the body `scope`, fix `clearPrefs`, nullable `writeValue`, `readValueBlocking`
- [x] `BasePrefsHelper`: `dispatcher` param, fix `clearPrefs`
- [x] Delete both companion `supervisorJob` properties (grepped — no references in this repo)
- [x] Update class KDoc on both
- [x] Update the library's own `app/` sample module if it passes a context — it doesn't; no change needed
- [x] Grep consumers for `coroutineContext` / `supervisorJob` usage — clean within this repo
- [ ] Fix the AIM app's logout call site in the same release — **not in this repo**
- [x] Migration note in README (`## Migrating to 2.0`)
- [ ] Major version bump, tag + JitPack release, then bump `prefshelperVersion` in consumers
- [x] Deterministic tests proving the injectable DataStore delivers goal 2

---

## Notes for whoever picks this up

There is an **uncommitted** change in `AIM-Capture-Android`
(`data/local/prefs/NormalDataStore.kt`) that works around the current API by passing a custom
`coroutineContext` to the superclass to attach an exception handler. It is a stopgap and becomes
obsolete once this redesign lands — expect to delete it and inject the app scope instead.

One thing that is **not** a defect, despite looking like one: `withContext(coroutineContext)` is a
real dispatcher switch, not a no-op. `coroutineContext` is a constructor property that *shadows*
`kotlin.coroutines.coroutineContext`. This was misdiagnosed once already.
