import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.dokka)
	alias(libs.plugins.kover)
	`maven-publish`
}

group = "com.github.projectdelta6"

configure<LibraryExtension> {
	namespace = "com.duck.prefshelper"
	compileSdk = libs.versions.compileSdk.get().toInt()

	defaultConfig {
		minSdk = 21

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		consumerProguardFiles("consumer-rules.pro")
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

	publishing {
		singleVariant("release") {
			withSourcesJar()
			withJavadocJar()
		}
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_11)
	}
}

afterEvaluate {
	publishing {
		publications {
			register<MavenPublication>("release") {
				from(components["release"])
			}
		}
	}
}

// JitPack strips the -sources classifier from published Gradle Module Metadata, so Gradle asks for
// a file that does not exist and IDEs fall back to decompiled classes. Without a .module, resolution
// goes through the POM, where sources are found by classifier convention. Verified in
// AppolyDroid-Toolbox 1.8.2.
tasks.withType<GenerateModuleMetadata>().configureEach {
	enabled = false
}

dependencies {
	implementation(libs.androidx.core.ktx)

	api(libs.androidx.dataStore)

	dokkaPlugin(libs.android.documentation.plugin)

	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.androidx.test.espresso.core)
}
