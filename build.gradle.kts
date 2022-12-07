plugins {
    kotlin("jvm") version "1.7.21"
    application
}

group = "dev.luna5ama"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")

    implementation("it.unimi.dsi:fastutil:8.5.9")

    implementation("org.ow2.asm:asm-commons:9.4")
    implementation("org.ow2.asm:asm-tree:9.4")
}

kotlin {
    val jvmArgs = mutableSetOf<String>()
    (rootProject.findProperty("kotlin.daemon.jvm.options") as? String)
        ?.split("\\s+".toRegex())?.toCollection(jvmArgs)
    System.getProperty("gradle.kotlin.daemon.jvm.options")
        ?.split("\\s+".toRegex())?.toCollection(jvmArgs)
    kotlinDaemonJvmArgs = jvmArgs.toList()
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }
}

application {
    mainClass.set("MainKt")
}