import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The whole app except its shell: learning engine, state and every screen, for Android (the app module) and the
// browser (wasmJs, what iPhones open).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "dev.hamper.uli.shared"
        compileSdk = 37
        minSdk = 33
        androidResources { enable = true } // the fonts
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("uli")
        browser { commonWebpackConfig { outputFileName = "uli.js" } }
        binaries.executable()
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }

    sourceSets {
        val commonMain by getting
        val androidMain by getting { dependsOn(commonMain) }
        val wasmJsMain by getting { dependsOn(commonMain) }

        commonMain.dependencies {
            api(libs.cmp.runtime)
            api(libs.cmp.foundation)
            api(libs.cmp.ui)
            api(libs.cmp.material3)
            implementation(libs.cmp.icons.core)
            implementation(libs.androidx.graphics.shapes)
            api(libs.cmp.lifecycle.viewmodel)
            api(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

// What the web app downloads besides its code, one source for both platforms: the questions and the fonts (scripts/web.sh
// then narrows the fonts to what the app uses).
val webFiles = tasks.register<Sync>("webFiles") {
    from(rootProject.layout.projectDirectory.file("content/questions.json"))
    from(layout.projectDirectory.dir("src/androidMain/res/font"))
    into(layout.buildDirectory.dir("generated/webFiles"))
}
kotlin.sourceSets.named("wasmJsMain") { resources.srcDir(webFiles) }
