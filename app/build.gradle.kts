import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing key material lives outside the repo (see .gitignore: keystore.properties,
// *.jks). Absent on a fresh checkout/dev machine, so the release build type falls back to debug
// signing rather than failing — see the `signingConfig` assignment below.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.mandallaz.pikadex"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mandallaz.pikadex"
        minSdk = 24
        targetSdk = 36
        // SemVer — app isn't published yet, so this starts pre-1.0. Bump versionName per release
        // and versionCode by 1 each time a build is uploaded to the Play Store.
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real release signing when keystore.properties is present (see top of file);
            // otherwise falls back to debug-signed so the release variant still installs and is
            // runtime-verifiable (R8 breaking Gson reflection would otherwise go unnoticed until
            // an actual release) on a machine without the real signing key.
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Networking: calls to PokeAPI
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    // okhttp3 classes (OkHttpClient, Interceptor, Cache...) are imported directly across several
    // data/remote files — this used to rely entirely on retrofit's transitive okhttp dependency,
    // with no guarantee retrofit keeps pulling in a compatible version.
    implementation(libs.okhttp)
    // Logging every request/response body is debug-only noise (and a minor info leak) in a
    // release build; it has no place shipping to users.
    debugImplementation(libs.okhttp.logging.interceptor)

    // Loading Pokémon sprites/artwork
    implementation(libs.coil.compose)
    // Animated GIF decoding for Showdown battle sprites (F38)
    implementation(libs.coil.gif)

    // Custom Tabs: opens Smogon links in-app instead of a separate browser task
    implementation(libs.androidx.browser)
}
