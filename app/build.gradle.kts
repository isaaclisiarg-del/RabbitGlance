plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.isaaclisi.rabbitglance"
 compileSdk = 35
 buildToolsVersion = "35.0.0"
 defaultConfig { applicationId = "com.isaaclisi.rabbitglance"; minSdk = 35; targetSdk = 35; versionCode = 1; versionName = "0.1.0-black-hole" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") } }
}
dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.robolectric:robolectric:4.16.1") }
