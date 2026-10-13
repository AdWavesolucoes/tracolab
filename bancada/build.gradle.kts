// Bancada de desempenho na JVM (a mesma medição feita no tablet).
plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":nucleo"))
    implementation(testFixtures(project(":nucleo")))
}

application { mainClass.set("tracolab.bancada.BancadaKt") }
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
