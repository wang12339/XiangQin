pluginManagement {
    repositories {
        google()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
    plugins {
        kotlin("android") version "2.0.10" apply false
        kotlin("plugin.compose") version "2.0.10" apply false
        kotlin("plugin.serialization") version "2.0.10" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "XiangQin"
include(":app")
