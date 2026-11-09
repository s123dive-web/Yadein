plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.test)
    api(platform(libs.junit5.bom))
    api(libs.junit5.jupiter)
    api(libs.turbine)
}
