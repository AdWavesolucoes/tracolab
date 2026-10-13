// Núcleo de cálculo: Kotlin puro, sem dependência de Android (ADR 0006).
plugins {
    kotlin("jvm")
    `java-library`
    `java-test-fixtures`
}

kotlin { jvmToolchain(21) }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

dependencies {
    api("org.matheclipse:matheclipse-core:3.0.0")          // Symja, LGPL-3.0 (ADR 0005)
    testFixturesApi("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("tracolab.spec", rootProject.file("spec/casos-comportamento.json").absolutePath)
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
