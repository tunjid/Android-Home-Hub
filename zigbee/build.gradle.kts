plugins {
    `android-module-convention`
}

//android {
//    sourceSets {
//        main.res.srcDirs 'res', 'res-public'
//    }
//}


kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(project(":common"))
                implementation(project(":protocols"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.zigbee.core)
                implementation(libs.zigbee.core)
                implementation(libs.zigbee.console)
                implementation(libs.zigbee.serial)
                implementation(libs.zigbee.dongle.cc2531)
                implementation(libs.zigbee.dongle.ember)

                implementation(libs.gson)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.zigbee.core)
                implementation(libs.zigbee.core)
                implementation(libs.zigbee.console)
                implementation(libs.zigbee.serial)
                implementation(libs.zigbee.dongle.cc2531)
                implementation(libs.zigbee.dongle.ember)

                implementation(libs.gson)
            }
        }
    }
}

