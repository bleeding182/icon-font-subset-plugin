plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = "com.davidmedenjak.fontsubsetting"
version = providers.gradleProperty("version").getOrElse("1.0.0-SNAPSHOT")

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.kotlin.compiler.embeddable)

    testImplementation(libs.junit)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val copyNativeLibraries by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("native-libs-staging")) {
        include("**/*.so", "**/*.dll", "**/*.dylib")
    }
    into(layout.buildDirectory.dir("resources/main/native"))
}

tasks.named<ProcessResources>("processResources") {
    from(copyNativeLibraries)
}

gradlePlugin {
    website.set("https://github.com/bleeding182/icon-font-subset-plugin")
    vcsUrl.set("https://github.com/bleeding182/icon-font-subset-plugin.git")
    
    plugins {
        create("fontSubsetting") {
            id = "com.davidmedenjak.fontsubsetting"
            implementationClass = "com.davidmedenjak.fontsubsetting.plugin.FontSubsettingPlugin"
            displayName = "Font Subsetting Plugin"
            description = "Gradle plugin for automatic font subsetting based on usage in Android apps"
            tags.set(listOf("android", "fonts", "optimization", "subsetting", "kotlin", "variable-fonts", "harfbuzz"))
        }
    }
}

tasks.test {
    systemProperty("test.font.path",
        layout.projectDirectory.file("../demo/symbolfonts/MaterialSymbolsOutlined.ttf").asFile.absolutePath)
    systemProperty("test.codepoints.path",
        layout.projectDirectory.file("../demo/symbolfonts/MaterialSymbolsOutlined.codepoints").asFile.absolutePath)
    maxHeapSize = "1g"
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Font Subsetting Plugin")
            description.set("Gradle plugin for automatic font subsetting based on usage in Android apps")
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
