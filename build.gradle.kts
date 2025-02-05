import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
    application
}

group = "app.simplecloud"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(rootProject.libs.kotlinTest)
    implementation(rootProject.libs.kotlinJvm)
    implementation(rootProject.libs.kotlinCoroutines)
    implementation(rootProject.libs.bundles.log4j)
    implementation(rootProject.libs.bundles.configurate)
    implementation(rootProject.libs.clikt)
    implementation(rootProject.libs.github)
    implementation(rootProject.libs.okhttp)
}

application {
    mainClass = "app.simplecloud.updater.launcher.LauncherKt"
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}

tasks.named("shadowJar", ShadowJar::class) {
    mergeServiceFiles()

    archiveFileName.set("${project.name}.jar")
}

tasks.test {
    useJUnitPlatform()
}