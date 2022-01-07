plugins {
    `android-module-convention`
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.core)

    implementation(libs.tunjid.mutator.core)
    implementation(libs.tunjid.mutator.coroutines)

//    implementation parent.ext.usbSerialA
//    implementation parent.ext.usbSerialB
}
