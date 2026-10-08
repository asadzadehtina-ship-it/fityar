plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ============ شناسه‌های AdMob ============
// نسخه debug همیشه تبلیغ تستی دارد. برای انتشار، فقط این سه مقدار را با شناسه‌های
// واقعی حساب AdMob خودت عوض کن (الان تستی‌اند و درآمد ندارند).
val releaseAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val releaseBannerId = "ca-app-pub-3940256099942544/6300978111"
val releaseAppOpenId = "ca-app-pub-3940256099942544/9257395921"
// ========================================

android {
    namespace = "com.example.gymguide"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fityar.app"
        minSdk = 26
        targetSdk = 35   // قبل از آپلود با حداقل مورد نیاز Play Console چک کن
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "APP_OPEN_ID", "\"ca-app-pub-3940256099942544/9257395921\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = releaseAdmobAppId
            buildConfigField("String", "BANNER_ID", "\"$releaseBannerId\"")
            buildConfigField("String", "APP_OPEN_ID", "\"$releaseAppOpenId\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.google.android.gms:play-services-ads:23.5.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")
}
