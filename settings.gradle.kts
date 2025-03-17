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
        maven {
            url = uri("https://s3-us-west-2.amazonaws.com/mimik-android-repo")
        }
    }
}

rootProject.name = "mimik AI Chat"
include(":app")
 