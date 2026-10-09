plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tyh24647.devtools"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.tyh24647.devtools"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["admobAppId"] =
            providers
                .gradleProperty("admobAppId")
                .getOrElse("ca-app-pub-3940256099942544~3347511713")
        buildConfigField(
            "boolean",
            "ADS_ENABLED",
            providers.gradleProperty("devtoolsAds").getOrElse("false"),
        )
        buildConfigField(
            "String",
            "BILLING_PUBLIC_KEY",
            "\"${providers.gradleProperty("billingPublicKey").getOrElse("")}\"",
        )
        buildConfigField(
            "String",
            "AD_BANNER",
            "\"${providers.gradleProperty("adBanner").getOrElse("ca-app-pub-3940256099942544/6300978111")}\"",
        )
        buildConfigField(
            "String",
            "AD_INTERSTITIAL",
            "\"${providers.gradleProperty("adInterstitial").getOrElse("ca-app-pub-3940256099942544/1033173712")}\"",
        )
        buildConfigField(
            "String",
            "AD_OPEN",
            "\"${providers.gradleProperty("adOpen").getOrElse("ca-app-pub-3940256099942544/9257395921")}\"",
        )
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "PREVIEW_PRO", "true")
        }
        release {
            isMinifyEnabled = true
            buildConfigField("boolean", "PREVIEW_PRO", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    // Override the old Fragment pulled transitively by SDKs; ActivityResult requires 1.3+ .
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
}
