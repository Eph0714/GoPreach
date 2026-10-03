import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")
}

// TomTom map tiles experiment — the key lives only in local.properties
// (gitignored, never committed) and is exposed to Kotlin as a BuildConfig
// constant rather than hardcoded in source, same reasoning as any other
// per-developer secret. Note this does NOT make the key safe from
// extraction: it still ends up embedded in plain text inside the WebView
// HTML string the app loads at runtime, readable by anyone who inspects
// network traffic or decompiles the APK — there is no way to fully hide a
// client-side map-tile key, this is just "don't also leak it via git history."
val tomtomApiKey: String = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) localPropertiesFile.inputStream().use { load(it) }
}.getProperty("tomtomApiKey", "")

android {
    namespace = "com.emfitsolutions.gopreach"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.emfitsolutions.gopreach"
        minSdk = 24
        targetSdk = 35
        versionCode = 191
        versionName = "1.130.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "TOMTOM_API_KEY", "\"$tomtomApiKey\"")
        // TomTom's "complete" flavor needs no extra credentials (unlike
        // "extended", which TomTom only grants on request) — see
        // settings.gradle.kts for the Maven repo this resolves against.
        missingDimensionStrategy("tomtom-sdk-version", "complete")
        // TomTom only ships native libs for these two ABIs anyway (its docs'
        // own requirement) — without this filter Gradle still packages every
        // other ABI's .so from every other dependency, unsplit, into one APK.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // GoPreach is sideloaded (no Play Store), so every release has always
    // been signed with the standard Android debug key -- confirmed by
    // comparing v1.109.0's actual signer cert against
    // ~/.android/debug.keystore's (identical SHA-256). Wiring it in here so
    // `assembleRelease` produces an already-signed APK instead of silently
    // shipping unsigned (the "parser did not find any certificates" install
    // failure this fixes). Every dev machine building a release therefore
    // needs its own local debug.keystore -- present automatically on any
    // machine that has ever run/debugged an Android app from Android
    // Studio or `gradlew`.
    val debugKeystore = file(System.getProperty("user.home") + "/.android/debug.keystore")

    signingConfigs {
        getByName("debug") {
            storeFile = debugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = debugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            // No applicationIdSuffix: debug and release share one package ID
            // (and one signing key) so installing either build updates the
            // existing app instead of adding a second copy on the phone.
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
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Output "GoPreach.apk" instead of Gradle's default "app-release.apk" /
    // "app-debug.apk" naming -- both variants share the name (they land in
    // separate outputs/apk/<debug|release>/ folders so there's no clash) so
    // every GitHub release always ships an asset literally named
    // "GoPreach.apk", regardless of which build type actually produced it.
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "GoPreach.apk"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core / Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    // AppCompatActivity — MainActivity extends it for androidx.biometric.BiometricPrompt's
    // FragmentManager requirement (see MainActivity.kt's own comment).
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Biometric sign-in + encrypted "remember me" credential storage (login screen).
    implementation("androidx.fragment:fragment-ktx:1.8.4")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Room (offline cache)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager (offline sync queue)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Location / Maps (Share Location, GPS capture)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Native TomTom Maps SDK (Territory Assignment boundary preview, native
    // polygon rendering) — map-display-premium is the View-based artifact;
    // the official Compose wrapper has no polygon API as of 2.6.2. Requires
    // API 26+/arm64-v8a or x86_64/Vulkan 1.0, so every call site must check
    // com.emfitsolutions.gopreach.ui.components.map.NativeMapSupport before
    // touching any class from this dependency — see that file's doc comment.
    implementation("com.tomtom.sdk.maps:map-display-premium:2.6.2")
    // MapOptions' simple mapKey-only constructor resolves its map-tile data
    // provider through the SDK's global context — without TomTomSdk.initialize()
    // having run first, MapView.onCreate throws "No valid data provider
    // configured" (confirmed on-device), so this is not actually optional
    // despite TomTom's own docs never saying so explicitly.
    implementation("com.tomtom.sdk:init:2.6.2")

    // Coil (logo / image loading)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // JSON for the offline cache/outbox payloads (see data/local)
    implementation("com.google.code.gson:gson:2.11.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
