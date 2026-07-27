import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val releaseSigningDirectory = rootProject.file("../.secrets/release-signing")
val releaseSigningProperties = Properties().apply {
    val file = releaseSigningDirectory.resolve("release-signing.properties")
    if (file.isFile) file.inputStream().use(::load)
}
val releaseKeyStore = releaseSigningDirectory.resolve("plankton-release.jks")
val releaseSigningReady = releaseKeyStore.isFile && listOf("storePassword", "keyAlias", "keyPassword").all(releaseSigningProperties::containsKey)

android {
    namespace = "com.voiceassistant"
    compileSdk = 36
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.voiceassistant"
        minSdk = 34
        targetSdk = 36
        versionCode = 39
        versionName = "3.9"

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

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = releaseKeyStore
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
        aidl = true
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

tasks.configureEach {
    if (name.contains("Release", ignoreCase = true)) {
        doFirst {
            check(releaseSigningReady) { "未找到完整的正式签名材料：${releaseSigningDirectory.absolutePath}" }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation(project(":bridge"))
    implementation(files("libs/sherpa-onnx-static-1.12.21.aar"))

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
