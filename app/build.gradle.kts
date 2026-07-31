import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kover)
}

configure<ApplicationExtension> {
	namespace = "com.duck.app"
	compileSdk = libs.versions.compileSdk.get().toInt()

	defaultConfig {
		applicationId = "com.duck.app"
		minSdk = 26
		targetSdk = libs.versions.targetSdk.get().toInt()
		versionCode = 1
		versionName = "1.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

		// Test-APK-only rules. When instrumented tests target the minified release build, AGP
		// minifies the test APK too, and Espresso's transitive Error Prone annotations reference
		// javax.lang.model.*, which does not exist on Android. Kept out of proguard-rules.pro on
		// purpose: the app's own release build needs no keep rules, and that is the claim under test.
		testProguardFiles("proguard-rules-androidtest.pro")
	}

	buildTypes {
		release {
			// On deliberately: the library README promises consumers need no ProGuard rules of
			// their own, and this is the only place in the repo that puts R8 anywhere near the
			// library's reflective paths (Enum.name / enumConstants). If this build starts needing
			// keep rules, that promise is broken.
			isMinifyEnabled = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			// Debug-signed so the minified build can actually be installed for the manual
			// instrumented run below. This is a sample app that is never published.
			signingConfig = signingConfigs.getByName("debug")

			// Harness-only keeps, added ONLY for the instrumented-test run. Kept conditional so
			// that a plain `assembleRelease` — the shipped configuration — still proves the
			// library needs no keep rules of its own.
			if (providers.gradleProperty("minifiedTests").isPresent) {
				proguardFile("proguard-rules-instrumentation.pro")
			}
		}
	}

	// Instrumented tests normally run against debug, which is not minified and so cannot say
	// anything about R8. Opt in with -PminifiedTests to point them at the release build instead:
	//
	//   ./gradlew :app:connectedAndroidTest -PminifiedTests
	//
	// Deliberately not the default and deliberately not in CI — it needs a device, and CI has none.
	if (providers.gradleProperty("minifiedTests").isPresent) {
		testBuildType = "release"
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}

	buildFeatures {
		compose = true
	}

	testOptions {
		unitTests {
			// Required for Robolectric: BaseDataStoreHelperTest runs on the JVM and
			// needs the merged manifest + resources on the unit-test classpath.
			isIncludeAndroidResources = true
		}
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_11)
	}
}

kover {
	reports {
		// Coverage gate: `./gradlew :app:koverVerifyDebug` fails below this floor.
		// Aggregated line coverage across com.duck.prefshelper.* (both helpers, now
		// all JVM-tested via Robolectric). Set below current to catch regressions
		// without tripping on minor refactors; raise as coverage improves.
		verify {
			rule {
				minBound(70)
			}
		}
		filters {
			includes {
				// Measure only the published library. The sample app (incl. the
				// consumer com.duck.app.data.prefs.* classes, which the tests don't
				// instantiate) is scaffolding, not the code under test.
				classes("com.duck.prefshelper.*")
			}
		}
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime)
	implementation(libs.androidx.activity.compose)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.compose.material3)

//    implementation(libs.androidx.dataStore)
	implementation(project(":PrefsHelper"))

	// Sample only — the library itself is DI-agnostic and depends on no DI framework.
	// Used to demonstrate injecting an application-lifetime scope and guaranteeing the
	// one-instance-per-DataStore-file rule structurally instead of via a hand-rolled singleton.
	implementation(libs.koin.android)

	// Aggregate the library's coverage into this module's Kover report,
	// since the tests that exercise PrefsHelper live here in :app.
	kover(project(":PrefsHelper"))

	// Unit tests run on the JVM. BaseDataStoreHelperTest runs under Robolectric via the
	// AndroidJUnit4 delegating runner, so DataStore coverage is visible to Kover (which
	// cannot instrument on-device tests). No emulator required.
	testImplementation(libs.junit)
	testImplementation(libs.mockito.core)
	testImplementation(libs.mockito.kotlin)
	testImplementation(libs.androidx.test.ext.junit)
	testImplementation(libs.robolectric)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.koin.test)

	// Instrumented tests exist solely to verify the library survives R8. See testBuildType above.
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.androidx.test.espresso.core)
	androidTestImplementation(libs.androidx.tracing)

	debugImplementation(libs.androidx.compose.ui.tooling)
}
