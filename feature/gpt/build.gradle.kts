
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Function to load environment variables from the .env file
fun loadEnvVariables(): Map<String, String> {
  val envFile = project.file(".env")
  if (!envFile.exists()) return emptyMap()

  return envFile.readLines()
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .map { it.split("=", limit = 2) }
    .associate { it[0].trim() to it.getOrElse(1) { "" }.trim() }
}

// Load environment variables
val envVariables = loadEnvVariables()
val openAiApiKey: String = envVariables["OPENAI_API_KEY"] ?: "key_not_found"


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
        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiApiKey\"")
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
  jcenter()
  // Add this if it's not already present
  maven(url = "https://www.jitpack.io")  // Use parentheses and 'url =' for Kotlin DSL
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
    implementation("com.azure:azure-ai-openai:1.0.0-beta.10")
    implementation("com.android.billingclient:billing:7.0.0")


    implementation(platform("com.google.firebase:firebase-bom:33.1.1"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-crashlytics")

}
