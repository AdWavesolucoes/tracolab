// API da página: JSON de entrada (regiões em LaTeX + posição) → estado de cada região.
// A mesma API atende a ponte do app Android, o servidor de desenvolvimento e o webservice (ADR 0006).
plugins {
    kotlin("jvm")
    `java-library`
}

kotlin { jvmToolchain(21) }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

dependencies {
    api(project(":nucleo"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
