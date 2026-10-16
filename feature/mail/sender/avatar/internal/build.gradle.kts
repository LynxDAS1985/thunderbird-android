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

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
}
