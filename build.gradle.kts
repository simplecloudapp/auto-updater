import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
    alias(libs.plugins.sonatype.central.portal.publisher)
    `maven-publish`
    application
}

group = "app.simplecloud.updater"
version = determineVersion()

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

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}

tasks {
    withType<JavaCompile> {
        options.isFork = true
        options.isIncremental = true
    }

    named("shadowJar", ShadowJar::class) {
        mergeServiceFiles()

        archiveFileName.set("${project.name}.jar")
    }

    test {
        useJUnitPlatform()
    }
}

centralPortal {
    name = project.name

    username = project.findProperty("sonatypeUsername") as? String
    password = project.findProperty("sonatypePassword") as? String

    pom {
        name.set("SimpleCloud Player Auto-Updater")
        description.set("The SimpleCloud Auto Updater is a powerful tool that manages automatic updates for SimpleCloud components.")
        url.set("https://github.com/simplecloudapp/auto-updater")

        developers {
            developer {
                id.set("SimpleCloud Developers")
            }
        }
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        scm {
            url.set("https://github.com/simplecloudapp/auto-updater.git")
            connection.set("git:git@github.com:simplecloudapp/auto-updater.git")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "simplecloud"
            url = uri(determineRepositoryUrl())
            credentials {
                username = System.getenv("SIMPLECLOUD_USERNAME")
                    ?: (project.findProperty("simplecloudUsername") as? String)
                password = System.getenv("SIMPLECLOUD_PASSWORD")
                    ?: (project.findProperty("simplecloudPassword") as? String)
            }

            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

signing {
    val releaseType = project.findProperty("releaseType")?.toString() ?: "snapshot"
    if (releaseType != "release") {
        return@signing
    }

    if (hasProperty("signingPassphrase")) {
        val signingKey: String? by project
        val signingPassphrase: String? by project
        useInMemoryPgpKeys(signingKey, signingPassphrase)
    } else {
        useGpgCmd()
    }

    sign(publishing.publications)
}

fun determineVersion(): String {
    val baseVersion = project.findProperty("baseVersion")?.toString() ?: "0.0.0"
    val releaseType = project.findProperty("releaseType")?.toString() ?: "snapshot"
    val commitHash = System.getenv("COMMIT_HASH") ?: "local"

    return when (releaseType) {
        "release" -> baseVersion
        "rc" -> "$baseVersion-rc.$commitHash"
        "snapshot" -> "$baseVersion-SNAPSHOT.$commitHash"
        else -> "$baseVersion-SNAPSHOT.local"
    }
}

fun determineRepositoryUrl(): String {
    val baseUrl = "https://repo.simplecloud.app/"
    return when (project.findProperty("releaseType")?.toString() ?: "snapshot") {
        "release" -> "$baseUrl/releases"
        "rc" -> "$baseUrl/rc"
        else -> "$baseUrl/snapshots"
    }
}