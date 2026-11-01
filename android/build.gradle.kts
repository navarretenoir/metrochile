// Metro para Android (Kotone · desarrollado por Javiera Navarrete). Sin dependencias externas (apto para F-Droid).
plugins { id("com.android.application") version "9.4.1" }
val assetsUi = layout.buildDirectory.dir("assets-ui").get().asFile
val prepararUi by tasks.registering(Sync::class) { into(assetsUi); from(rootDir.resolve("../ui")) { into("ui") } }
android {
    namespace = "org.kotone.metro"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "org.kotone.metro"
        minSdk = 21          // Android 5.0: la mayor compatibilidad; lo moderno va protegido por versión
        targetSdk = 36
        versionCode = 2
        versionName = "2.1"
    }
    sourceSets["main"].apply { manifest.srcFile("AndroidManifest.xml"); java.srcDirs("src"); res.srcDirs("res"); assets.srcDirs(assetsUi) }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { abortOnError = false; checkOnly += setOf("NewApi", "InlinedApi"); xmlReport = true }
}
tasks.named("preBuild") { dependsOn(prepararUi) }
