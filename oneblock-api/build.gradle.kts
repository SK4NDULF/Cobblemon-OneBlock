plugins {
    id("fabric-loom")
    `java-library`
    `maven-publish`
}

base {
    archivesName = "oneblock-api"
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

// Addon-Devs entwickeln gegen dieses Artefakt (Publishing-Ziel wird in Phase 11 final konfiguriert).
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "oneblock-api"
            from(components["java"])
        }
    }
}
