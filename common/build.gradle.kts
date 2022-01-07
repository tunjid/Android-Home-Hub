plugins {
    `android-module-convention`
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.core)

    implementation(libs.tunjid.mutator.core.jvm)
    implementation(libs.tunjid.mutator.coroutines.jvm)

//    implementation parent.ext.usbSerialA
//    implementation parent.ext.usbSerialB
}
