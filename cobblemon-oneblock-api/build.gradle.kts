plugins {
    id("fabric-loom")
    `java-library`
    `maven-publish`
}

base {
    archivesName = "cobblemon-oneblock-api"
}

repositories {
    // Loom fügt Fabric- und Mojang-Repos automatisch hinzu.
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

// Addon developers build against this artifact. `./gradlew publish` pushes it to
// GitHub Packages; JitPack works without any extra configuration because the module
// is a plain java-library publication.
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "cobblemon-oneblock-api"
            from(components["java"])

            pom {
                name = "Cobblemon OneBlock API"
                description = "Public API for the Cobblemon OneBlock core mod: events, managers and extension points."
                url = "https://github.com/SK4NDULF/Cobblemon-OneBlock"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
                scm {
                    url = "https://github.com/SK4NDULF/Cobblemon-OneBlock"
                    connection = "scm:git:https://github.com/SK4NDULF/Cobblemon-OneBlock.git"
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/SK4NDULF/Cobblemon-OneBlock")
            credentials {
                // Set via env vars or ~/.gradle/gradle.properties — never commit these.
                username = (project.findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR")
                password = (project.findProperty("gpr.key") as String?) ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
