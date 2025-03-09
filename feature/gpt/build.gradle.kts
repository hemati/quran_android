
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.appcoholic.gpt"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    packaging {
        resources {
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

repositories {
  google()
  mavenCentral()
  // Add this if it's not already present
  maven(url = "https://www.jitpack.io")  // Use parentheses and 'url =' for Kotlin DSL
  maven(url = "https://repo.grails.org/grails/core/")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
//    implementation(project(":app"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("com.github.stfalcon-studio:Chatkit:0.4.1")
    implementation("com.openai:openai-java:0.33.0")

    implementation("com.android.billingclient:billing:7.0.0")


    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-config")

    implementation("com.google.android.play:review:2.0.1")

}
