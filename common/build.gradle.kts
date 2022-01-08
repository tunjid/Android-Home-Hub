plugins {
    id("com.squareup.sqldelight")
    `android-module-convention`
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

                implementation(libs.square.sqldelight.coroutines.extensions)

                implementation(libs.tunjid.mutator.core.common)
                implementation(libs.tunjid.mutator.coroutines.common)
            }
        }
        named("androidMain") {
            dependencies {
                implementation(libs.usbserialport.android.felHR85)
                implementation(libs.usbserialport.android.mik3y)
            }
        }
        named("desktopMain") {
            dependencies {
                implementation(libs.usbserialport.jvm.jssc)
            }
        }
    }
}

sqldelight {
    database("StringKeyValueDatabase") {
        packageName = "com.tunjid.rcswitchcontrol.common.data"
        schemaOutputDirectory = file("build/dbs")
    }
}

