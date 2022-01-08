plugins {
    `android-module-convention`
}

//    implementation parent.ext.usbSerialA
//    implementation parent.ext.usbSerialB

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)

                implementation(libs.tunjid.mutator.core.jvm)
                implementation(libs.tunjid.mutator.coroutines.jvm)
            }
        }
    }
}

