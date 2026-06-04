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
		debug {
			// Lets Kover pick up coverage from the instrumented BaseDataStoreHelperTest
			// (requires a connected device/emulator when running koverHtmlReport).
			enableAndroidTestCoverage = true
		}
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
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_11)
	}
}

kover {
	reports {
		filters {
			excludes {
				// The sample app's UI scaffolding isn't the library under test —
				// keep the coverage number focused on com.duck.prefshelper.*
				classes(
					"*.R", "*.R\$*", "*.BuildConfig",
					"com.duck.app.ui.*",
					"com.duck.app.MainActivity*",
					"com.duck.app.ComposableSingletons*",
				)
				annotatedBy("androidx.compose.runtime.Composable")
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

	testImplementation(libs.junit)
	testImplementation(libs.mockito.core)
	testImplementation(libs.mockito.kotlin)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.androidx.test.espresso.core)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	debugImplementation(libs.androidx.compose.ui.tooling)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
}
