plugins {
    `android-module-convention`
}

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

                implementation(libs.tunjid.androidx.comms)
            }
        }
    }
}
