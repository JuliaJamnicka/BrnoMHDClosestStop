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
        // Huawei Wear Engine SDK
        maven("https://developer.huawei.com/repo/")
    }
}
rootProject.name = "BrnoMHD"
include(":app")
