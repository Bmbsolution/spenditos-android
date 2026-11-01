import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Push needs the owner's Firebase project: with app/google-services.json present the Google Services
// plugin wires FCM; without it the app builds and runs with push inert (see docs/RELEASE.md).
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

/** Loads config/<env>.properties (dev.properties is local-only and optional). */
fun envConfig(name: String): Properties = Properties().apply {
    val file = rootProject.file("config/$name.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Values that must never reach a deployed host (mirrors
// spenditos/packages/core/src/config/client-config.ts FORBIDDEN_HOST_TOKENS).
val forbiddenHostTokens = listOf("example.com", "placeholder", "changeme", "todo", "your-api", "localhost")

/** API_HOST / WEB_SOCKET_HOST are host-only: no scheme, no path, no forbidden token. */
fun validatedHost(env: String, key: String, value: String?, required: Boolean): String {
    val host = value?.trim().orEmpty()
    if (host.isEmpty()) {
        check(!required) { "config/$env.properties: $key is required" }
        return ""
    }
    check(!host.contains("://")) { "config/$env.properties: $key must be host-only (no scheme): $host" }
    check(!host.contains('/')) { "config/$env.properties: $key must be host-only (no path): $host" }
    check(forbiddenHostTokens.none { host.lowercase().contains(it) }) {
        "config/$env.properties: $key contains a placeholder/forbidden token: $host"
    }
    return host
}

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.spenditos.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.asaklex.spenditos"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    flavorDimensions += "env"
    productFlavors {
        listOf("staging", "prod", "harness", "local").forEach { env ->
            create(env) {
                dimension = "env"
                val cfg = envConfig(env)
                val harness = env == "harness"
                // `local` = real sign-in/refresh flow against the e2e sidecar on 127.0.0.1 (debug only).
                val localUrl = harness || env == "local"
                val apiHost = if (localUrl) "" else validatedHost(env, "API_HOST", cfg.getProperty("API_HOST"), required = true)
                val wsHost = if (localUrl) "" else validatedHost(env, "WEB_SOCKET_HOST", cfg.getProperty("WEB_SOCKET_HOST"), required = false)
                val apiBaseUrl = if (localUrl) cfg.getProperty("API_BASE_URL") else "https://$apiHost"
                buildConfigField("String", "API_BASE_URL", quoted(apiBaseUrl))
                buildConfigField("String", "WEB_SOCKET_URL", quoted(if (wsHost.isEmpty()) "" else "wss://$wsHost"))
                buildConfigField("String", "DEPLOYMENT_STAGE", quoted(cfg.getProperty("DEPLOYMENT_STAGE", env)))
                buildConfigField("String", "APP_ENV_NAME", quoted(cfg.getProperty("APP_ENV_NAME", env)))
                buildConfigField("String", "CLIENT_CONFIG_VERSION", quoted(cfg.getProperty("CLIENT_CONFIG_VERSION", "unset")))
                buildConfigField("boolean", "SHOW_ENV_BADGE", cfg.getProperty("SHOW_ENV_BADGE", "true"))
                buildConfigField("boolean", "IS_HARNESS", harness.toString())
                // RevenueCat public SDK key for Android (Play); blank keeps purchases off (owner-provided).
                buildConfigField("String", "REVENUECAT_API_KEY", quoted(cfg.getProperty("REVENUECAT_API_KEY", "")))
                buildConfigField("String", "HARNESS_AUTH_TOKEN", quoted(if (harness) cfg.getProperty("HARNESS_AUTH_TOKEN", "") else ""))
                if (env != "prod") applicationIdSuffix = ".$env"
                resValue("string", "app_name", if (env == "prod") "Spenditos" else "Spenditos ${cfg.getProperty("APP_ENV_NAME", env)}")
            }
        }
    }

    // Release signing comes only from the environment (never committed): see docs/RELEASE.md.
    // Without SPENDITOS_KEYSTORE_PATH the release APK/AAB stays unsigned.
    signingConfigs {
        val keystorePath = providers.environmentVariable("SPENDITOS_KEYSTORE_PATH").orNull
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("SPENDITOS_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SPENDITOS_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SPENDITOS_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            // -Pspenditos.minifyDebug exercises the release R8 rules on a debug-signed build.
            isMinifyEnabled = providers.gradleProperty("spenditos.minifyDebug").isPresent
            if (isMinifyEnabled) proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        // Every English string must ship in French (values-fr), and nothing may exist only in French.
        error += setOf("MissingTranslation", "ExtraTranslation")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
    }
}

// Never silently prod in debug: prodDebug does not exist, and the harness is debug-only.
androidComponents {
    beforeVariants { variant ->
        val env = variant.productFlavors.firstOrNull { it.first == "env" }?.second
        if (env == "prod" && variant.buildType == "debug") variant.enable = false
        if ((env == "harness" || env == "local") && variant.buildType == "release") variant.enable = false
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        optIn.addAll(
            "kotlinx.serialization.ExperimentalSerializationApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Per-app language on Android 9-12 (AppCompatDelegate.setApplicationLocales backport).
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.biometric)
    // Receipt viewfinder (iOS ScanReceiptView's live camera).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.revenuecat.purchases)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
