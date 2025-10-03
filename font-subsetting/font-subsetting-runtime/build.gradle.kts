plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

group = "com.davidmedenjak.fontsubsetting"
version = "1.0.0-SNAPSHOT"

android {
    namespace = "com.davidmedenjak.fontsubsetting.runtime"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                cppFlags += listOf(
                    "-std=c++17",
                    "-fvisibility=hidden",
                    "-ffunction-sections",
                    "-fdata-sections",
                    "-fno-exceptions",    // Disable C++ exceptions (reduces size)
                    "-fno-rtti"           // Disable RTTI (reduces size)
                )
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DANDROID_PLATFORM=android-24",
                    "-DANDROID_ARM_NEON=TRUE"
                )
            }
        }

        ndk {
            // For production: Consider reducing to only arm64-v8a for smaller APK size
            // Most modern devices (95%+) support arm64. This would reduce total library size by ~75%
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            externalNativeBuild {
                cmake {
                    cppFlags += listOf("-Os", "-flto=thin", "-DNDEBUG")
                    arguments("-DCMAKE_BUILD_TYPE=Release")
                }
            }
            packaging {
                jniLibs {
                    useLegacyPackaging = false
                }
            }
        }

        debug {
            externalNativeBuild {
                cmake {
                    arguments("-DCMAKE_BUILD_TYPE=Debug")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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

    packaging {
        jniLibs {
            useLegacyPackaging = false  // Enable 16KB page alignment
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.davidmedenjak.fontsubsetting"
                artifactId = "font-subsetting-runtime"
                version = project.version.toString()

                pom {
                    name.set("Font Subsetting Runtime")
                    description.set("Runtime library for loading and animating font paths in Compose")
                    url.set("https://github.com/bleeding182/icon-font-subset-plugin")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("davidmedenjak")
                            name.set("David Medenjak")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/bleeding182/icon-font-subset-plugin.git")
                        developerConnection.set("scm:git:ssh://github.com/bleeding182/icon-font-subset-plugin.git")
                        url.set("https://github.com/bleeding182/icon-font-subset-plugin")
                    }
                }
            }
        }
    }
}
