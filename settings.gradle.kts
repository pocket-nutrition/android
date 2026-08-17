// SPDX-License-Identifier: MPL-2.0

pluginManagement {
    repositories {
        google {
            // Narrowed so an artefact outside these groups cannot be resolved from Google's
            // repository — the standard AGP template guard against typo-squatting.
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Fail rather than silently honour a per-project repository declaration.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pocket-nutrition-android"
include(":app")
