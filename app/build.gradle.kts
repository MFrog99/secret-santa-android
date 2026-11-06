import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** CI passes values through the environment, local builds through local.properties. */
fun secret(name: String): String {
    val value = System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
    return requireNotNull(value) { "$name is missing: set it in local.properties or the environment" }
}

android {
    namespace = "io.github.mfrog99.secretsanta"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mfrog99.secretsanta"
        minSdk = 26
        targetSdk = 37
        // CI run number keeps versionCode growing, so an installed APK can be updated in place.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("appVersionName").get()

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }

    buildTypes {
        release {
            // Direct APK distribution, no Play Store: signed with the debug key by design.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
