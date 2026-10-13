rootProject.name = "tracolab"

pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }

include(":nucleo", ":api", ":bancada", ":servidor-dev", ":compat:log4j-android")
