plugins {
    `android-module-convention`
//    id("com.squareup.sqldelight")
}

//    implementation parent.ext.usbSerialA
//    implementation parent.ext.usbSerialB

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.ktor.core)
                implementation(libs.ktor.network)

                implementation(libs.tunjid.mutator.core.common)
                implementation(libs.tunjid.mutator.coroutines.common)
            }
        }
    }
}

