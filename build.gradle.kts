plugins {
    id("fabric-loom") version "1.17.19" apply false
    kotlin("jvm") version "2.2.21" apply false
}

subprojects {
    group = property("maven_group") as String
    version = property("mod_version") as String
}
