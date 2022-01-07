plugins {
    `android-module-convention`
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.tunjid.androidx.comms)

    implementation(project(":common"))
    implementation(project(":protocols"))
}