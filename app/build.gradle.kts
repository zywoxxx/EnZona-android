import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// URL de la API: se lee de local.properties (no versionado). Vacía = la app usa datos de prueba en memoria.
//   enzona.api.url=http://10.0.2.2:8080/     (emulador: 10.0.2.2 es la PC donde corre el backend)
//   enzona.api.url=http://192.168.1.50:8080/ (teléfono físico: la IP de la PC en la red local)
val propiedadesLocales = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}
val urlApi: String = (propiedadesLocales.getProperty("enzona.api.url") ?: "").trim()
    .let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }

android {
    namespace = "com.uv.enzona"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.uv.enzona"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ENZONA_API_URL", "\"$urlApi\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // QR: generación local del código a partir del contenido firmado
    implementation(libs.zxing.core)

    // Validador: cámara + lectura de códigos QR en el acceso
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    // API REST del backend Spring Boot (ver data/remote/ApiService.kt y RemoteRepository.kt)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)   // portadas de eventos (URL de la API o imagen elegida en el teléfono)
    implementation(libs.androidx.security.crypto)

    // Ubicación exacta del evento en mapa (RF-08/RF-19): OpenStreetMap vía osmdroid, sin clave de API
    implementation(libs.osmdroid.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
