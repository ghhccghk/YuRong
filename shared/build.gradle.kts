import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    android {
       namespace = "top.ghhccghk.yadea.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()

       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.miuix.ui)
            // Optional: Add miuix-preference for preference components
            implementation(libs.miuix.preference)
            // Optional: Add miuix-icons for more icons
            implementation(libs.miuix.icons)
            // Optional: Add miuix-squircle for squircle (smooth rounded corner) shapes
            implementation(libs.miuix.squircle)
            // Optional: Add miuix-nav for navigation
            implementation(libs.miuix.nav)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.javacv.platform)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.testJunit)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnit()
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
