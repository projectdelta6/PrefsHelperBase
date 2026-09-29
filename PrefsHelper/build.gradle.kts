import com.android.build.api.dsl.LibraryExtension
import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.dokka)
	alias(libs.plugins.kover)
	alias(libs.plugins.vanniktech.publish)
}

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

}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_11)
	}
}

mavenPublishing {
	configure(
		AndroidSingleVariantLibrary(
			javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
			sourcesJar = SourcesJar.Sources(),
			variant = "release",
		),
	)
	publishToMavenCentral()
	signAllPublications()

	coordinates("dev.projectdelta6", "prefshelper", libs.versions.prefsHelperVersion.get())

	pom {
		name.set("PrefsHelper")
		description.set("Type-safe base classes for Android SharedPreferences and Jetpack DataStore<Preferences>, with Keystore-backed encrypted strings.")
		url.set("https://github.com/projectdelta6/PrefsHelperBase")
		inceptionYear.set("2023")

		licenses {
			license {
				name.set("GNU General Public License v3.0")
				url.set("https://www.gnu.org/licenses/gpl-3.0.html")
			}
		}

		developers {
			developer {
				id.set("projectdelta6")
				name.set("Bradley Duck")
				email.set("projectdelta6@gmail.com")
			}
		}

		scm {
			url.set("https://github.com/projectdelta6/PrefsHelperBase")
			connection.set("scm:git:git://github.com/projectdelta6/PrefsHelperBase.git")
			developerConnection.set("scm:git:ssh://git@github.com/projectdelta6/PrefsHelperBase.git")
		}
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)

	api(libs.androidx.dataStore)

	dokkaPlugin(libs.android.documentation.plugin)

	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.androidx.test.espresso.core)
}
