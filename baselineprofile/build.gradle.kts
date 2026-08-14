// F112 — scaffolding only, per issue #174: gets the module building with the right plugins,
// dependencies and target wiring in place. No real profile-generating content yet — that's #175
// (F113), split out after two prior Jules sessions stalled trying to do both at once.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.mandallaz.pikadex.baselineprofile"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 28 // Macrobenchmark's StartupTimingMetric requires API 28+.
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// F113 will add the real "benchmark" variant selection and generation target here — left at the
// plugin's defaults for now since there's no generator to point it at yet.
baselineProfile {
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
