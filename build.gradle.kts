import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "dev.fastmc"
version = "1.1.1"

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    id("dev.fastmc.maven-repo").version("1.0.0")
}

gradlePlugin {
    plugins {
        create("fast-remapper") {
            id = "dev.fastmc.fast-remapper"
            displayName = "fast-remapper"
            description = "Remap tools for Minecraft mods"
            implementationClass = "dev.fastmc.remapper.FastRemapperPlugin"
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.luna5ama.dev/")
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    implementation("com.google.code.gson:gson:2.10")
    implementation("it.unimi.dsi:fastutil:8.5.12")

    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")
    implementation("dev.fastmc:ow2-asm-ktdsl:1.0.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

kotlin {
    val jvmArgs = mutableSetOf<String>()
    (rootProject.findProperty("kotlin.daemon.jvm.options") as? String)
        ?.split("\\s+".toRegex())?.toCollection(jvmArgs)
    System.getProperty("gradle.kotlin.daemon.jvm.options")
        ?.split("\\s+".toRegex())?.toCollection(jvmArgs)
    kotlinDaemonJvmArgs = jvmArgs.toList()
}

tasks {
    test {
        useJUnitPlatform()
        val args = mutableSetOf("-Xmx2G")
        System.getProperty("org.gradle.jvmargs")
            ?.split("\\s+".toRegex())?.toCollection(args)
        (rootProject.findProperty("org.gradle.jvmargs") as? String)
            ?.split("\\s+".toRegex())?.toCollection(args)
        jvmArgs = args.toList()
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlin.contracts.ExperimentalContracts",
                "-Xlambdas=indy",
                "-Xjvm-default=all",
                "-Xbackend-threads=0"
            )
        }
    }
}