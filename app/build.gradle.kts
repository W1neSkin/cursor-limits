import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

fun signingProp(name: String): String? {
    val fromEnv = System.getenv(name)
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val fromFile = localProps.getProperty(name)
    return if (fromFile.isNullOrBlank()) null else fromFile
}

android {
    namespace = "app.cursorlimit"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.cursorlimit"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "2.0"
    }

    // The keystore and its password stay out of git. An update can replace
    // an installed app only when it is signed with the same key.
    val keystoreFile = file("release.keystore")
    val storePass = signingProp("CURSOR_LIMIT_STORE_PASSWORD")
    val keyPass = signingProp("CURSOR_LIMIT_KEY_PASSWORD") ?: storePass
    val hasReleaseKey = keystoreFile.exists() && !storePass.isNullOrBlank() && !keyPass.isNullOrBlank()

    if (hasReleaseKey) {
        signingConfigs {
            create("widget") {
                storeFile = keystoreFile
                storePassword = storePass
                keyAlias = signingProp("CURSOR_LIMIT_KEY_ALIAS") ?: "cursorlimit"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("widget")
            }
        }
        release {
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("widget")
            }
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
