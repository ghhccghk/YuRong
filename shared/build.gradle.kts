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
            // 系统返回手势 / 预测性返回：Android 由 Activity 的返回回调承接，其余平台由 Compose 宿主提供调度器
            implementation(libs.compose.uiBackhandler)
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
            // Miuix 官方模糊库：为顶栏/底栏提供背景模糊，运行时能力由 AppBlur 统一门控。
            implementation(libs.miuix.blur)
            // Miuix 着色器能力库：只用于查询 RenderEffect / RuntimeShader 的运行时支持。
            implementation(libs.miuix.shader)
            implementation(libs.materialkolor)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.javacv.platform)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.testJunit)
            // 离屏渲染测试（ImageComposeScene）需要 skiko 本地库；仅测试用，不影响其他目标平台。
            implementation(compose.desktop.currentOs)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
        wasmJsMain.dependencies {
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
