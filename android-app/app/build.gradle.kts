plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.Base64

val generateLauncherIcon by tasks.registering {
    val iconFile = layout.projectDirectory.file("../launcher-icon.base64")
    val outputDir = layout.buildDirectory.dir("generated/launcher-res")
    inputs.file(iconFile)
    outputs.dir(outputDir)
    doLast {
        val png = Base64.getDecoder().decode(iconFile.asFile.readText().filterNot { it.isWhitespace() })
        val target = outputDir.get().file("drawable-nodpi/gourmetal_icon.png").asFile
        target.parentFile.mkdirs()
        target.writeBytes(png)
    }
}

android {
    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/launcher-res"))
    namespace = "com.gourmetal.retroalerts"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    // Sign debug APKs with both legacy v1 and modern v2 schemes. This
    // improves compatibility with package installers that mishandle v2-only APKs.
    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.gourmetal.retroalerts"
        minSdk = 26
        targetSdk = 35
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
        versionCode = 19
        versionName = "0.8.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        getByName("debug") {
            // Keep the diagnostic build alongside the user's existing app.
            // This avoids uninstalling the installed app and losing its local data.
            applicationIdSuffix = ".diagnostic"
        }
    }

    buildFeatures {
        compose = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

tasks.named("preBuild").configure { dependsOn(generateLauncherIcon) }

dependencies {
    implementation(files("libs/discord_partner_sdk.aar"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
