plugins {
    kotlin("jvm") version "2.3.21" apply false
}

// Todo o projeto gera bytecode Java 17: é o mínimo que roda nas máquinas de destino
// (Debian 12 da OP4 só tem o Java 17) e no Android. Vale para qualquer módulo Kotlin novo.
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
            compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
}
