import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing credentials come from keystore.properties, which is deliberately
// not in git, or from environment variables so CI can sign without a checked-in file.
//
// If neither is present the release build is left unsigned rather than falling back
// to the debug key: a debug-signed build looks fine locally and is then rejected by
// Play, which is a slow way to discover the problem.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(key: String, envName: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(envName)

val releaseStoreFile = signingValue("storeFile", "KIDQUEST_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "KIDQUEST_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "KIDQUEST_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "KIDQUEST_KEY_PASSWORD")

val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() } && rootProject.file(releaseStoreFile!!).exists()

android {
    namespace = "se.kidquest.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "se.kidquest.app"
        minSdk = 24
        targetSdk = 36
        // Play rejects a version code that has already been uploaded, and the
        // 15 March 2026 closed-track build already used one. Override per build
        // rather than editing this file -- and pass BOTH, always:
        //   ./gradlew :app:bundleRelease -Pkidquest.versionCode=14 -Pkidquest.versionName=1.4
        //
        // The first thirteen releases passed only the version code, so every one of
        // them shipped as "1.0" and the Play Console listed thirteen identical names.
        // The code is what Play enforces; the name is what a human reads.
        versionCode = (findProperty("kidquest.versionCode") as String?)?.toInt() ?: 2
        versionName = findProperty("kidquest.versionName") as String? ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "KidQuest: no release signing config found - the release build " +
                        "will be unsigned and cannot be uploaded to Play. " +
                        "See android/KidQuest/keystore.properties.example."
                )
                null
            }
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.zxing.android.embedded)
    // Brings in Google Play Billing, which is also what puts
    // com.android.vending.BILLING in the merged manifest -- Play refuses to let you
    // create subscription products until it sees that in an uploaded build.
    implementation(libs.revenuecat.purchases)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}