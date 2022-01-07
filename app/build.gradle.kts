/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/*
apply plugin : "com.android.application"
apply plugin : "kotlin-android"
apply plugin : "kotlin-parcelize"
apply plugin : "kotlin-kapt"
apply from : "${project.rootDir}/androidConfig.gradle"
*/

import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    compileSdk = 31

    defaultConfig {
        minSdk = 21
        targetSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    sourceSets {
        named("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            res.srcDirs("src/androidMain/res")
        }
    }
    defaultConfig {
        applicationId = "com.tunjid.rcswitchcontrol"
    }
    signingConfigs {
        getByName("debug") {
            if (file("debugKeystore.properties").exists()) {
                val props = Properties()
                props.load(FileInputStream(file("debugKeystore.properties")))
                storeFile = file(props["keystore"]!!)
                storePassword = props["keystore.password"] as String
                keyAlias = props["keyAlias"]!! as String
                keyPassword = props["keyPassword"]!! as String
            }
        }
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.0.1"
    }
}

dependencies {
    implementation(libs.accompanist.flowlayout)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.window)

    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.material)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling)


    implementation(libs.google.material)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.tunjid.androidx.comms)
    implementation(libs.tunjid.mutator.core.jvm)


    implementation("com.squareup.picasso:picasso:2.71828")
//    implementation("com.github.QuadFlask:colorpicker:0.0.15)

    implementation(project(":common"))
    implementation(project(":zigbee"))
    implementation(project(":433mhz"))
    implementation(project(":protocols"))
}
