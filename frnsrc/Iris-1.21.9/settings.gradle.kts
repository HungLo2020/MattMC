package frnsrc.`Iris-1`.`21`.`9`

rootProject.name = "Iris"

pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
        gradlePluginPortal()
    }
}

include("common", "fabric", "neoforge")
