plugins {
    id("fabric-loom")
    java
}

base {
    archivesName = "oneblock-example-addon"
}

repositories {
    mavenCentral()
}

// This is what a real addon's dependency block looks like: Fabric + the OneBlock API.
// Note there is NO dependency on oneblock-core and none on Cobblemon.
dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    // In your own project this is a normal Maven coordinate:
    //   modImplementation("io.github.sk4ndulf.oneblock:oneblock-api:<version>")
    // Inside this repository we consume the sibling module directly instead.
    implementation(project(":oneblock-api", configuration = "namedElements"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
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
