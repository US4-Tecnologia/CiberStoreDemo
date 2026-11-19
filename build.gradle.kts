plugins {
    // Android
    id("com.android.application") version "8.7.3" apply false
    // Kotlin
    kotlin("multiplatform") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    // Compose Multiplatform
    id("org.jetbrains.compose") version "1.7.3" apply false
}
