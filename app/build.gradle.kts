import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing lives outside the repo so every build is signed with the same key.
val signingPath: String = System.getenv("ULI_SIGNING_PROPERTIES")
    ?: "${System.getProperty("user.home")}/.uli/android-signing.properties"
val signingProps = Properties().apply {
    val file = File(signingPath)
    if (file.isFile) file.inputStream().use { load(it) }
}
val hasReleaseSigning = signingProps.getProperty("storeFile") != null

android {
    namespace = "dev.hamper.uli"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.hamper.uli"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

// A release build without the signing key would come out unsigned (and not installable over the real app):
// fail before building anything instead. Debug builds do not need the key.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    val ok = hasReleaseSigning
    val path = signingPath
    doFirst {
        if (!ok) {
            throw GradleException(
                "Release signing is not set up: $path is missing or has no storeFile. " +
                    "Create it (storeFile, storePassword, keyAlias, keyPassword) or point ULI_SIGNING_PROPERTIES at one.",
            )
        }
    }
}

// content/questions.json is the single source of truth; it is bundled as an asset at build time.
abstract class CopyContentTask : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.mkdirs()
        source.get().asFile.copyTo(File(out, "questions.json"), overwrite = true)
    }
}

val copyContent = tasks.register<CopyContentTask>("copyContent") {
    source.set(rootProject.layout.projectDirectory.file("content/questions.json"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyContent, CopyContentTask::outputDir)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.core)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
