plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mandallaz.pikadex"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mandallaz.pikadex"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Not a Play Store build — debug-signed so the release variant can still be installed
            // and runtime-verified (R8 breaking Gson reflection would otherwise go unnoticed until
            // a real release).
            signingConfig = signingConfigs.getByName("debug")
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
