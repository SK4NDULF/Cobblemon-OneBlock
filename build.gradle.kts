plugins {
    id("fabric-loom") version "1.10-SNAPSHOT" apply false
    kotlin("jvm") version "2.1.20" apply false
}

subprojects {
    group = property("maven_group") as String
    version = property("mod_version") as String
}
