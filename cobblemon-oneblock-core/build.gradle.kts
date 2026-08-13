plugins {
    id("fabric-loom")
    kotlin("jvm")
}

base {
    archivesName = "cobblemon-oneblock-core"
}

repositories {
    // Loom fügt Fabric- und Mojang-Repos automatisch hinzu.
    mavenCentral()
    maven("https://maven.impactdev.net/repository/development/") { name = "ImpactDev (Cobblemon)" }
}

// Libraries, die mit ins Core-Jar gebündelt werden (jar-in-jar)
val bundledLibs = listOf(
    "com.zaxxer:HikariCP:${property("hikaricp_version")}",
    "org.xerial:sqlite-jdbc:${property("sqlite_jdbc_version")}",
    "org.mariadb.jdbc:mariadb-java-client:${property("mariadb_client_version")}",
    "blue.endless:jankson:${property("jankson_version")}",
)

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")

    modImplementation("com.cobblemon:fabric:${property("cobblemon_version")}")

    implementation(project(":cobblemon-oneblock-api", configuration = "namedElements"))
    include(project(":cobblemon-oneblock-api"))

    bundledLibs.forEach {
        implementation(it)
        include(it)
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
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
