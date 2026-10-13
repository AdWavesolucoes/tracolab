rootProject.name = "tracolab"

pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }

include(":nucleo", ":bancada", ":compat:log4j-android")
