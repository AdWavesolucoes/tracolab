rootProject.name = "tracolab"

pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }

include(":nucleo", ":api", ":bancada", ":servidor", ":compat:log4j-android")
