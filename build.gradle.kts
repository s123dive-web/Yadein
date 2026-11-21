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

// One KSP task at a time. KSP hands its work to background workers, so even with --no-parallel two modules were
// processed at once, and now and then one of them (usually feature:people) came out with no generated Hilt code and no
// error: the app then failed with "Cannot create an instance of PeopleViewModel". A shared service with one slot makes
// the KSP tasks queue up; everything else still runs as before.
abstract class KspQueue : BuildService<BuildServiceParameters.None>

val kspQueue = gradle.sharedServices.registerIfAbsent("kspQueue", KspQueue::class) {
    maxParallelUsages.set(1)
}

subprojects {
    tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
        usesService(kspQueue)
    }
}
