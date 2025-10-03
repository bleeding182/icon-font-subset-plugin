plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Use "local" for development (uses included build), or a version number for published releases
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
    kotlinOptions {
        jvmTarget = "11"
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
            // resourceName and fontFileName will default based on font file name
            // but we can override them if needed:
            resourceName.set("symbols")

            // Configure variable font axes
            axes {
                // Keep fill axis but limit to 0..1 range
                axis("FILL").range(0f, 1f, 0f)
                
                // Limit weight to 400-700 range (normal to bold)
                axis("wght").range(400f, 700f, 400f)
                
                // Remove grade axis completely
                axis("GRAD").remove()
                
                // Keep optical size but limit to 24-48 range
                axis("opsz").range(24f, 48f, 48f)
            }
            stripGlyphNames = true
            stripHinting = true
        }
    }
}

dependencies {
    // Font subsetting runtime library - from included build
    implementation("com.davidmedenjak.fontsubsetting:font-subsetting-runtime:1.0.0-SNAPSHOT")
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}