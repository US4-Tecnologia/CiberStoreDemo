import org.gradle.api.tasks.testing.Test
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("com.android.application")
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                // Ktor 3.0.3 = ultima serie sobre Kotlin 2.0.21 (3.1+ exige 2.1.x).
                // REST via body<String>() + kotlinx.serialization: sem plugin de
                // content-negotiation, menos dependencias e menos superficie de build.
                implementation("io.ktor:ktor-client-core:3.0.3")
                // ViewModel do CMP (decisao do solicitante na sessao 24/11).
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("io.ktor:ktor-client-okhttp:3.0.3")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("io.ktor:ktor-client-cio:3.0.3")
            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core-wasm-js:3.0.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:3.0.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
    }
}

android {
    namespace = "com.ciberstore.demo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ciberstore.demo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.ciberstore.demo.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "CiberStoreDemo"
            packageVersion = "1.0.0"
        }
    }
}

// Teste de integracao opt-in (M3): roda contra a API real quando
// -Dsmartorder.integration=true. Desligado por default para manter a suite offline.
tasks.withType<Test>().configureEach {
    systemProperty("smartorder.integration", System.getProperty("smartorder.integration") ?: "false")
    systemProperty(
        "smartorder.baseUrl",
        System.getProperty("smartorder.baseUrl") ?: "http://127.0.0.1:8099",
    )
}

// A desktopRun do KGP (KotlinJvmRun, usada como carrier pelo IDE) nao herda o
// mainClass do compose.desktop.application (que alimenta apenas a task `run` do CMP).
// O KGP reconfigura a task depois dos callbacks de realize; o whenReady e o ultimo
// momento antes da congelar as propriedades para execucao.
gradle.taskGraph.whenReady {
    (allTasks.find { it.name == "desktopRun" } as? JavaExec)
        ?.mainClass?.set("com.ciberstore.demo.MainKt")
}
