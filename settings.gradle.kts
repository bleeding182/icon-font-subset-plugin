pluginManagement {
    // Include the plugin build for composite builds - must be in pluginManagement for plugin resolution
    includeBuild("plugin")
    
    repositories {
        mavenLocal() // For local SNAPSHOT builds
        
        // Load credentials from local.properties
        val localProperties = java.util.Properties()
        val localPropertiesFile = file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }
        
        maven("https://maven.pkg.github.com/bleeding182/icon-font-subset-plugin") {
            credentials {
                username = localProperties.getProperty("gpr.user") ?: System.getenv("GPR_USER")
                password = localProperties.getProperty("gpr.token") ?: System.getenv("GPR_TOKEN")
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.davidmedenjak.fontsubsetting") {
                // Use included build for "local" version, Maven resolution for others
                if (requested.version != "local") {
                    useModule("com.davidmedenjak.fontsubsetting:font-subsetting-plugin:${requested.version}")
                }
                // For "local" version, the included build will be used automatically
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
//        mavenLocal() // For local SNAPSHOT builds
        google()
        mavenCentral()
    }
}

rootProject.name = "Font Subsetting"
include(":demo")

// Include runtime library as a separate build
includeBuild("compose-glyphs")
