// SPDX-License-Identifier: MPL-2.0

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing is opt-in and absent by default.
 *
 * `key.properties` and the keystore it points at are gitignored and exist only on the machine that
 * holds them. When the file is missing — every CI run that is not a tagged release, and every fresh
 * clone — the release build produces an unsigned APK rather than failing. That keeps the pipeline
 * useful without the secret, and makes the signed build an explicit, deliberate step.
 *
 * Gradle instruments File.exists() as a configuration-cache input, so the cache invalidates by
 * itself the moment the file appears.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("key.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "org.pocketnutrition"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.pocketnutrition"
        // 26 rather than a lower floor so java.time is available without desugaring — the food diary
        // is date-keyed, and LocalDate arithmetic is not somewhere to accept a shim.
        minSdk = 26
        targetSdk = 37
        // CalVer: YYYY.MM.PATCH, matching the git tag (tag "v2026.08.1" -> versionName "2026.08.1").
        //
        // versionCode has to be a monotonically increasing Int, and a store rejects any upload that does
        // not strictly exceed the last one, so it is derived from the same three numbers rather than
        // maintained separately: YYYYMM * 100 + PATCH. That orders correctly across month and year
        // boundaries (2026.08.1 -> 20260801, then 2026.09.1 -> 20260901, then 2027.01.1 -> 20270101) and
        // leaves room for 99 releases within a month, well under the 2_100_000_000 store ceiling.
        //
        // Bump both together and keep them in step with the tag: an artefact that misreports its own
        // version cannot be traced back to the commit that built it.
        versionCode = 20_260_804
        versionName = "2026.08.4"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v1 is only needed below API 24; minSdk is 26, so v2 + v3 is the whole story and
                // dropping v1 keeps the APK smaller and verification faster.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else null
        }
    }

    // The generated design tokens are vendored from pocket-nutrition-contracts rather than
    // hand-written, so they live outside src/ to make that obvious in a file listing. The vendored
    // JSON is registered as test resources so the behavioural corpus is readable from the classpath.
    sourceSets {
        getByName("main") {
            // AGP 9 no longer compiles Kotlin found in a `java` source directory, so the vendored
            // tokens have to be registered on the `kotlin` container explicitly. Under AGP 8 the
            // java entry was enough, which is why this looks redundant and is not.
            kotlin.directories.addAll(listOf("src/main/kotlin", "../contract/kotlin"))
        }
        getByName("test") {
            resources.directories.add("../contract/data")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        // Off by default in AGP 8, and needed for two things the about screen cannot fake:
        // BuildConfig.VERSION_NAME, so the version shown to the user is the one the store shows
        // rather than a literal that drifts; and BuildConfig.DEBUG, which is what keeps the design
        // token gallery out of a release binary. Both are compile-time constants, so the release
        // build's dead branch is removed rather than shipped and guarded at runtime.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // A warning nobody has to act on is a warning everybody ignores.
        warningsAsErrors = true
        abortOnError = true

        // These three ask "is a newer version available?", which depends on the date and on the
        // network rather than on this commit. Combined with warningsAsErrors they would turn CI red
        // the day Google publishes a release, with nothing in the repository having changed — a gate
        // that fails for reasons the author cannot act on stops being a gate.
        //
        // They are disabled from a position of being current, not to excuse staleness: every version
        // in gradle/libs.versions.toml was the latest available when last verified, with lint
        // reporting zero issues. Dependency currency belongs to a deliberate upgrade pass, and there
        // is a real one waiting — see the AGP 9 notes in AGENTS.md.
        disable += setOf("NewerVersionAvailable", "AndroidGradlePluginVersion", "GradleDependency")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // Product photos in the suggestion list. Both artefacts are required: coil-compose gives the
    // AsyncImage composable, coil-network-okhttp gives it an HTTP fetcher. With only the first, Coil
    // resolves local URIs and every remote image fails without an obvious error.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Barcode scanning: CameraX for the live preview/frame pipeline, ML Kit for on-device decoding.
    // See the version catalogue for why this was chosen over the Play-Services Code Scanner.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(libs.junit)
}
