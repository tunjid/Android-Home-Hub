plugins {
    `android-module-convention`
}

//    implementation parent.ext.usbSerialA
//    implementation parent.ext.usbSerialB

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)

                implementation(libs.ktor.core)
                implementation(libs.ktor.network)

                implementation(libs.tunjid.mutator.core.common)
                implementation(libs.tunjid.mutator.coroutines.common)
            }
        }
    }
}

