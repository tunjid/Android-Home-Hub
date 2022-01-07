plugins {
    `android-module-convention`
}

//android {
//    sourceSets {
//        main.res.srcDirs 'res', 'res-public'
//    }
//}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.zigbee.core)
    implementation(libs.zigbee.core)
    implementation(libs.zigbee.console)
    implementation(libs.zigbee.serial)
    implementation(libs.zigbee.dongle.cc2531)
    implementation(libs.zigbee.dongle.ember)

    implementation(libs.gson)

    implementation(project(":common"))
    implementation(project(":protocols"))
}
