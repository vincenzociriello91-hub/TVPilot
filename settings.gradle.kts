pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "TVPilot"
// App per orologio (cartella wear/) sospesa dal 15/11/2026: per riattivarla aggiungere ":wear"
include(":app")
