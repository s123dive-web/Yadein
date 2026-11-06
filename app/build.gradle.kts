plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.yaadein.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yaadein.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // Release signing is configured only when the CI secrets are present (see .github/workflows/release.yml).
        create("release") {
            val storeFilePath = System.getenv("YAADEIN_KEYSTORE_PATH")
            if (storeFilePath != null && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("YAADEIN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("YAADEIN_KEY_ALIAS")
                keyPassword = System.getenv("YAADEIN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*")
        jniLibs.useLegacyPackaging = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    lint {
        checkDependencies = true
        abortOnError = true
        warningsAsErrors = false
        xmlReport = true
        htmlReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:lock"))
    implementation(project(":feature:timeline"))
    implementation(project(":feature:people"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:capture"))
    implementation(project(":feature:relive"))
    implementation(project(":feature:recap"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.hilt.work)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.androidx.window)
    implementation(libs.coil.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.work.runtime)
    implementation(libs.biometric)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)
    screenshotTestImplementation(libs.compose.ui.tooling)
    screenshotTestImplementation(libs.paging.compose)
    screenshotTestImplementation(libs.screenshot.validation.api)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.junit5.vintage)
    testRuntimeOnly(libs.junit5.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// ---- Zero-network guard -------------------------------------------------------------------
// Fails the build if any library sneaks a network permission into the merged manifest.
abstract class VerifyNoNetworkPermissionTask : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val text = mergedManifest.get().asFile.readText()
        val forbidden = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_SMS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        )
        val found = forbidden.filter { Regex("uses-permission[^>]*\"${Regex.escape(it)}\"").containsMatchIn(text) }
        report.get().asFile.writeText(if (found.isEmpty()) "OK: no forbidden permissions\n" else "FORBIDDEN: $found\n")
        if (found.isNotEmpty()) {
            throw GradleException("Yaadein must stay 100% offline. Forbidden permissions in merged manifest: $found")
        }
    }
}

androidComponents {
    onVariants { variant ->
        // Name the APK after the app: Yaadein-v<version>-<buildType>.apk
        val versionName = android.defaultConfig.versionName ?: "0"
        variant.outputs.forEach { output ->
            output.outputFileName.set("Yaadein-v$versionName-${variant.buildType}.apk")
        }
        val capitalized = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyNoNetworkPermissionTask>("verifyNoNetworkPermission$capitalized") {
            group = "verification"
            description = "Fails if the merged ${variant.name} manifest contains network or storage permissions."
            mergedManifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/no-network/${variant.name}.txt"))
        }
        tasks.matching { it.name == "assemble$capitalized" || it.name == "bundle$capitalized" }.configureEach { dependsOn(verify) }
    }
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(tasks.matching { it.name.startsWith("verifyNoNetworkPermission") }) }
