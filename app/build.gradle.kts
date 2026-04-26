plugins {
    alias(libs.plugins.android.application) // 1. Must be first
    alias(libs.plugins.kotlin.android)     // 2. Must be second to claim the 'kotlin' name
    alias(libs.plugins.kotlin.compose)     // 3. Compose adds to it
    alias(libs.plugins.kotlin.ksp)         // 4. Processing
    alias(libs.plugins.hilt.android)
    id("com.google.gms.google-services")// 5. Injection
}
android {
    namespace = "com.anantva.tether"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.anantva.tether"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

//  Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Icons
    implementation("androidx.compose.material:material-icons-extended")

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Room
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")
}
