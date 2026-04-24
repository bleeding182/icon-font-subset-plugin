import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
    alias(libs.plugins.maven.publish.vanniktech)
}

group = "com.davidmedenjak.fontsubsetting"
version = providers.gradleProperty("version").getOrElse("1.0.0-SNAPSHOT")

android {
    namespace = "com.davidmedenjak.fontsubsetting.runtime"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_static",
                    "-DCMAKE_BUILD_TYPE=MinSizeRel",
                )
                abiFilters("armeabi-v7a", "arm64-v8a", "x86_64")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
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

mavenPublishing {
    configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = true))
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(group.toString(), "font-subsetting-runtime", version.toString())

    pom {
        name.set("Font Subsetting Runtime")
        description.set("Android runtime library for font subsetting with HarfBuzz and Compose glyph rendering")
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

publishing {
    repositories {
        val ghUrl = findProperty("githubPackagesUrl") as String?
        if (ghUrl != null) {
            maven(ghUrl) {
                name = "GitHubPackages"
                credentials {
                    username = findProperty("githubPackagesUsername") as String? ?: ""
                    password = findProperty("githubPackagesPassword") as String? ?: ""
                }
            }
        }
    }
}

// Maven Central requires signed artifacts; GitHub Packages does not. Skip signing
// when no key is configured so GitHub Packages publishes succeed without credentials.
tasks.withType<Sign>().configureEach {
    onlyIf { project.hasProperty("signingInMemoryKey") || project.hasProperty("signing.keyId") }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation("androidx.compose.animation:animation-core")
}

// Fails a publish run if host-JVM native libraries are missing from
// src/main/resources/native/. Without these, Compose previews (and Paparazzi /
// Roborazzi) crash with UnsatisfiedLinkError because the AAR only has jniLibs
// for Android ABIs. Build them via: cd plugin && ./build-in-docker.sh
val verifyHostNatives = tasks.register("verifyHostNatives") {
    group = "verification"
    description = "Ensures host-JVM native libraries are bundled before publishing."

    val nativesRoot = layout.projectDirectory.dir("src/main/resources/native")
    // Bundled with a neutral extension so AGP doesn't strip them from classes.jar
    // (it excludes *.so/*.dll/*.dylib from library Java resources by default).
    // The loader renames back to the OS-native extension at extraction time.
    val expected = listOf(
        "linux-x86_64",
        "windows-x86_64",
        "darwin-x86_64",
        "darwin-aarch64",
    )

    doLast {
        val missing = expected.filterNot { platform ->
            nativesRoot.file("$platform/glyphruntime.bin").asFile.exists()
        }
        if (missing.isNotEmpty()) {
            val list = missing.joinToString("\n  ") { "$it/glyphruntime.bin" }
            throw GradleException(
                """
                |Host-JVM native libraries missing from ${nativesRoot.asFile}:
                |  $list
                |
                |Build them with: cd plugin && ./build-in-docker.sh
                |""".trimMargin()
            )
        }
    }
}

tasks.matching { it.name.startsWith("publish") && it.name.contains("Publication") }
    .configureEach { dependsOn(verifyHostNatives) }

