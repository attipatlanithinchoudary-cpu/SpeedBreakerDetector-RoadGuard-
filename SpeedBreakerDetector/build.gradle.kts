// This block now uses aliases from libs.versions.toml. This is clean and correct.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.maps.secrets) apply false
    id("com.google.gms.google-services") version "4.4.4" apply false

}
