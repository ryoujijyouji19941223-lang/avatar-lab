plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.totomarujapan.crowavatar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.totomarujapan.rabbitavatar.chatv01"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-chat"
    }

    signingConfigs {
        create("crowTest") {
            storeFile = file("crow-test.jks")
            storePassword = "crowavatar-test"
            keyAlias = "crowtest"
            keyPassword = "crowavatar-test"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("crowTest")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
 
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("com.google.mediapipe:tasks-vision:0.10.35")
}
