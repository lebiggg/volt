// Top-level build file — common config for all sub-projects.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.ksp)                 apply false
}
