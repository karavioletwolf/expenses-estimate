pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("file:///data/data/com.itsaky.androidide/files/home/maven/localMvnRepository") }
    }
}

rootProject.name = "ExpensesEstimate"
include(":app")
