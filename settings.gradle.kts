pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Paykit SDK (private). A read:packages token comes from gpr.user / gpr.key in
        // ~/.gradle/gradle.properties or from GITHUB_ACTOR / GITHUB_TOKEN; never from this repo.
        maven {
            url = uri("https://maven.pkg.github.com/Bmbsolution/paykit")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
            content { includeGroup("com.paykit") }
        }
    }
}

rootProject.name = "spenditos-android"
include(":app")
