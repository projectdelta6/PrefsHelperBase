# Applied to the APP's release build ONLY when running instrumented tests (-PminifiedTests).
# Never part of a shipped release, and nothing here is required by PrefsHelper.
#
# Why it has to live on the app side rather than in testProguardFiles: AGP does not duplicate
# classes into the test APK that are already on the app's compile classpath. androidx.tracing
# arrives transitively via AndroidX, so it is excluded from the test APK — and then R8 strips it
# from the app APK because the app itself never calls it. AndroidJUnitRunner.onCreate then dies
# with NoClassDefFoundError before a single test runs.
#
# Keeping this in a separate file, applied conditionally, is what preserves the actual signal:
# `./gradlew :app:assembleRelease` — the shipped configuration — still needs no keep rules at all.
# Each of these is a symbol the *test* code needs but the app does not, so R8 removes it from the
# app APK and AGP never duplicates it into the test APK. Rather than discovering them one crash at
# a time, keep the libraries wholesale.
#
# Crucially this does NOT weaken what the test verifies: `com.duck.**` — the sample's helpers, the
# enums, and PrefsHelper itself — is still shrunk and obfuscated, which is why
# testIsActuallyRunningMinified asserts on the runtime class name rather than trusting the setup.
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class androidx.** { *; }
-keep class org.jetbrains.** { *; }
-keep class org.koin.** { *; }
-dontwarn androidx.test.**
-dontwarn kotlin.**

# The instrumented test calls helper methods the sample app itself never calls (clearPrefs,
# setByteArray, migrateIfNeeded…). R8 rightly removes them as unused, and the test APK — compiled
# against the unminified API — then dies with NoSuchMethodError. This is an artefact of testing an
# app that is smaller than its test surface, not a library problem.
#
# `-keepclassmembers`, not `-keep`: the CLASSES are still renamed (NormalPrefs -> lj), so
# testIsActuallyRunningMinified still detects obfuscation, and the enum constants under test are
# untouched by this rule.
-keepclassmembers class com.duck.app.data.prefs.** { *; }
