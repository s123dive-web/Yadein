// Top-level build file. Plugins are declared here (apply false) so every module shares one version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.screenshot) apply false
}

// Robolectric downloads android-all jars at test time; point it at Google's Maven Central mirror to
// avoid rate limiting, and pin the SDK level it emulates.
subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
        systemProperty("robolectric.dependency.repo.id", "google-maven-central-mirror")
        systemProperty("robolectric.graphicsMode", "NATIVE")
        maxHeapSize = "2g"
        failOnNoDiscoveredTests = false
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }
}
