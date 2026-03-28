plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.davidmedenjak.fontsubsetting") version "local"
}

android {
    namespace = "com.davidmedenjak.fontsubsetting"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.davidmedenjak.fontsubsetting"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

fontSubsetting {
    fonts {
        create("materialSymbols") {
            fontFile.set(file("symbolfonts/MaterialSymbolsOutlined.ttf"))
            codepointsFile.set(file("symbolfonts/MaterialSymbolsOutlined.codepoints"))
            className.set("com.davidmedenjak.fontsubsetting.MaterialSymbols")
            resourceName.set("symbols")

            axes {
                axis("FILL").range(0f, 1f, 0f)
                axis("wght").range(400f, 700f, 400f)
                axis("GRAD").range(-25f, 200f, 0f)
                axis("opsz").range(24f, 48f, 48f)
            }
            stripGlyphNames = true
            stripHinting = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(project(":runtime"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}