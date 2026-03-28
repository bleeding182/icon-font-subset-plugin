plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.davidmedenjak.fontsubsetting.benchmark"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.davidmedenjak.fontsubsetting.benchmark"
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    buildTypes {
        debug {
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Benchmark library — must be implementation so manifest merges into test APK
    implementation(libs.androidx.benchmark.junit4)

    // Runtime module with HarfBuzz native library
    implementation(project(":runtime"))

    implementation(libs.androidx.core.ktx)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
}
