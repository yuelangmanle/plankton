plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.voiceassistant"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.voiceassistant"
        minSdk = 34
        targetSdk = 34
        versionCode = 32
        versionName = "3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += listOf("bin", "onnx")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(project(":bridge"))
    implementation(files("libs/sherpa-onnx-static-1.12.21.aar"))

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Keep historical debug APKs per version.
afterEvaluate {
    val vName = android.defaultConfig.versionName ?: "0.0.0"
    val vCode = android.defaultConfig.versionCode
    val archiveDir = rootProject.layout.projectDirectory.dir("apk_history/debug")
    val releaseArchiveDir = rootProject.layout.projectDirectory.dir("apk_history/release")

    val archiveTask = tasks.register<Copy>("archiveDebugApk") {
        dependsOn("assembleDebug")
        from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
        into(archiveDir)
        rename { "voice-assistant-debug-v${vName}(${vCode}).apk" }
    }

    tasks.named("assembleDebug").configure { finalizedBy(archiveTask) }

    val archiveReleaseTask = tasks.register<Copy>("archiveReleaseApk") {
        dependsOn("assembleRelease")
        from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
        into(releaseArchiveDir)
        rename { "voice-assistant-release-v${vName}(${vCode}).apk" }
    }

    tasks.named("assembleRelease").configure { finalizedBy(archiveReleaseTask) }
}
