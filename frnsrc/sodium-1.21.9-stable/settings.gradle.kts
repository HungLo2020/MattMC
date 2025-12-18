package frnsrc.`sodium-1`.`21`.`9-stable`

rootProject.name = "sodium"

pluginManagement {
    repositories {
        mavenLocal()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
        gradlePluginPortal()
    }
}

include("common")
include("fabric")
include("neoforge")
