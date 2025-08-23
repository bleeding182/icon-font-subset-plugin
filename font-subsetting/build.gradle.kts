plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    `maven-publish`
}

allprojects {
    group = "com.davidmedenjak.fontsubsetting"
    // Use explicit version for Gradle Plugin Portal releases, SNAPSHOT for GitHub Packages
    // Priority: 1. Gradle property, 2. Environment variable, 3. Default SNAPSHOT
    version = project.findProperty("version") as String? 
        ?: System.getenv("PLUGIN_VERSION") 
        ?: "1.0.0-SNAPSHOT"
    
    // Configure publishing for all subprojects
    afterEvaluate {
        if (plugins.hasPlugin("maven-publish")) {
            configure<PublishingExtension> {
                repositories {
                    // GitHub Packages
                    maven {
                        name = "GitHubPackages"
                        url = uri(project.findProperty("githubPackagesUrl") 
                            ?: "https://maven.pkg.github.com/bleeding182/icon-font-subset-plugin")
                        credentials {
                            username = project.findProperty("githubPackagesUsername") as String? 
                                ?: System.getenv("GITHUB_ACTOR")
                            password = project.findProperty("githubPackagesPassword") as String? 
                                ?: System.getenv("GITHUB_TOKEN")
                        }
                    }
                    
                    // Maven Local (for development)
                    mavenLocal()
                }
            }
        }
    }
}
