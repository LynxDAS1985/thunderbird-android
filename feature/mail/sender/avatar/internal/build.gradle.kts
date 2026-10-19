plugins {
    id(ThunderbirdPlugins.Library.android)
}

android {
    namespace = "net.thunderbird.feature.mail.sender.avatar.internal"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(projects.feature.mail.sender.avatar.api)
    implementation(libs.androidsvg)
    implementation(libs.minidns.hla)
    implementation(libs.okhttp)
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
