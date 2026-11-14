
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
        val png = Base64.getDecoder().decode(iconFile.asFile.readText().trim())
        val target = outputDir.get().file("drawable-nodpi/gourmetal_icon.png").asFile
        target.parentFile.mkdirs()
        target.writeBytes(png)
    }
}

android {
    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/launcher-res"))
    namespace = "com.gourmetal.retroalerts"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gourmetal.retroalerts"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.6.6"
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
    }
}

tasks.named("preBuild").configure { dependsOn(generateLauncherIcon) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
