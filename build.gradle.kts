// SPDX-License-Identifier: MPL-2.0
//
// Root build. Plugins are declared with `apply false` so the version catalogue stays the single
// source of versions while each module opts in to only what it needs.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
