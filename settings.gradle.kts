pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "cobblemon-oneblock"

include("cobblemon-oneblock-api", "cobblemon-oneblock-core")

// Reference addon for third-party developers. Built alongside the mod so a broken
// API change fails the build instead of silently rotting in the docs.
include("example-addon")
