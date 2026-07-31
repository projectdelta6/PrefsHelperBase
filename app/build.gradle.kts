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
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
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

	debugImplementation(libs.androidx.compose.ui.tooling)
}
