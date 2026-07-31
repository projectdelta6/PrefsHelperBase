# R8 rules for the androidTest APK only — NOT for the app, and NOT anything consumers need.
#
# Applied via `testProguardFiles`, so it affects only the instrumented-test APK built when
# `-PminifiedTests` points instrumented tests at the minified release build.
#
# Espresso pulls in Guava, which pulls in Error Prone annotations, which reference
# javax.lang.model.* — a compile-time-only API that does not exist on Android. R8 warns about the
# dangling reference while minifying the test APK. The app's own release build needs no such rule:
# this is test tooling, not PrefsHelper.
#
# Do not migrate these into proguard-rules.pro. Keeping them separate is what preserves the signal
# that the library itself requires no keep rules.
# Leave the test APK alone entirely. The app under test stays fully minified and obfuscated —
# that is the thing being verified — but shrinking the *test* APK only strips the JUnit runner's
# reflective entry points and proves nothing. These two lines scope R8 to the app.
-dontshrink
-dontobfuscate

-dontwarn javax.lang.model.element.Modifier

# The instrumentation runner, its JUnit plumbing and the test classes themselves are all reached
# reflectively by the framework, so R8 sees them as unused and strips them. Nothing here is a
# statement about PrefsHelper — it is what any project pays to run instrumented tests against a
# minified build.
-keep class androidx.test.** { *; }
-keep class androidx.tracing.** { *; }
-keep class org.junit.** { *; }
-keep class junit.** { *; }
-keep class org.hamcrest.** { *; }
-keep @org.junit.runner.RunWith public class * { *; }
-keep public class com.duck.app.R8SurvivalTest { *; }
-dontwarn androidx.test.**
-dontwarn org.junit.**
