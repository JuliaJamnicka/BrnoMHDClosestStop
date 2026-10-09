import java.security.KeyStore
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Build-time configuration: android/local.properties (not committed), Gradle -P properties or env vars.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun config(name: String, env: String, default: String): String =
    // an unset GitHub variable arrives as an empty string
    (findProperty(name) as String?) ?: localProps.getProperty(name) ?: System.getenv(env)?.takeIf { it.isNotEmpty() } ?: default
fun onlyAlias(path: String, password: String): String? = runCatching {
    KeyStore.getInstance(file(path), password.toCharArray()).aliases().toList().singleOrNull()
}.getOrNull()
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "io.github.juliajamnicka.fcil"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.juliajamnicka.fcil"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "DEFAULT_API_URL", quoted(config("mhdApiUrl", "MHD_API_URL", "https://mhd-api-620350272938.europe-west1.run.app")))
        buildConfigField("String", "DEFAULT_API_KEY", quoted(config("mhdApiKey", "MHD_API_KEY", "")))
        // Package name and signing fingerprint of the watch app, used by Wear Engine to pair the two apps.
        buildConfigField("String", "WATCH_PACKAGE", quoted(config("watchPackage", "WATCH_PACKAGE", "io.github.juliajamnicka.fcil.watch")))
        buildConfigField("String", "WATCH_FINGERPRINT", quoted(config("watchFingerprint", "WATCH_FINGERPRINT", "")))
    }

    // Wear Engine identifies this app by its signing certificate, so builds that talk to the watch
    // must always be signed with the same key. Configure it in local.properties or env vars.
    val keystorePath = config("signingStoreFile", "SIGNING_STORE_FILE", "")
    val stableSigning = if (keystorePath.isNotEmpty()) signingConfigs.create("stable") {
        storeFile = file(keystorePath)
        storePassword = config("signingStorePassword", "SIGNING_STORE_PASSWORD", "")
        // a keystore made for this app holds one key; use it whatever its alias (e.g. the earlier "brnomhd")
        keyAlias = config("signingKeyAlias", "SIGNING_KEY_ALIAS", onlyAlias(keystorePath, storePassword ?: "") ?: "fcil")
        keyPassword = config("signingKeyPassword", "SIGNING_KEY_PASSWORD", "")
    } else null

    buildTypes {
        debug {
            stableSigning?.let { signingConfig = it }
        }
        release {
            stableSigning?.let { signingConfig = it }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.huawei.wearengine)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
