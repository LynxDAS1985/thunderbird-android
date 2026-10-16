plugins {
    id(ThunderbirdPlugins.Library.jvm)
    alias(libs.plugins.android.lint)
}

dependencies {
    implementation(projects.feature.mail.sender.avatar.api)
    implementation(libs.minidns.hla)
    implementation(libs.okhttp)

    testImplementation(libs.okhttp.mockwebserver)
}
