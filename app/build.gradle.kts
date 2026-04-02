import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.google.firebase.appdistribution)
}

android {
    namespace = "com.example.anda"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.anda"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val caApiBaseUrl = providers.gradleProperty("CA_API_BASE_URL").orElse("").get()
        val caOfficialConsultUrl = providers.gradleProperty("CA_OFFICIAL_CONSULT_URL").orElse("").get()
        buildConfigField("String", "CA_API_BASE_URL", "\"$caApiBaseUrl\"")
        buildConfigField("String", "CA_OFFICIAL_CONSULT_URL", "\"$caOfficialConsultUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // If a keystore.properties file is present at the project root,
            // the signingConfig named "testRelease" will be applied. This
            // enables local generation of a signed APK for testing without
            // forcing a keystore into source control.
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProps = Properties().apply {
                    load(keystorePropertiesFile.inputStream())
                }

                // Resolve path and trim values to avoid accidental whitespace causing missing-file errors.
                val storeFilePath = keystoreProps.getProperty("storeFile")?.trim().orEmpty()
                val resolvedStoreFile = when {
                    storeFilePath.isBlank() -> rootProject.file("test-keystore.jks")
                    file(storeFilePath).isAbsolute -> file(storeFilePath)
                    else -> rootProject.file(storeFilePath)
                }

                if (resolvedStoreFile.exists()) {
                    signingConfig = signingConfigs.create("testRelease").apply {
                        storeFile = resolvedStoreFile
                        storePassword = keystoreProps.getProperty("storePassword")?.trim().orEmpty()
                        keyAlias = keystoreProps.getProperty("keyAlias")?.trim().orEmpty()
                        keyPassword = keystoreProps.getProperty("keyPassword")?.trim().orEmpty()
                    }
                } else {
                    // Warn during configuration when the referenced keystore is missing so the build can continue
                    logger.warn("Keystore file for testRelease not found: ${resolvedStoreFile.absolutePath}. Release will be unsigned.")
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.crashlytics)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.firebase.config)
    implementation("com.google.firebase:firebase-messaging:25.0.1")
    ksp(libs.androidx.room.compiler)
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)

    // PDF Generation & Digital Signatures
    implementation("com.itextpdf:itext-core:8.0.2")  // PDF generation (smaller, more reliable)
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")  // Crypto provider for signatures
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")  // PKIX support for certificates

    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.4.0")
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
}