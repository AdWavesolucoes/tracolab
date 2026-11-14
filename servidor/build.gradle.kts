// Servidor do TraçoLab (ADR 0019): página, API de cálculo, contas com 2FA e folhas por usuário,
// num processo só, com os dados em SQLite. É o que a imagem Docker e o serviço systemd rodam.
plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(21) }

val ktor = "3.6.1"

dependencies {
    implementation(project(":api"))
    implementation("io.ktor:ktor-server-core-jvm:$ktor")
    implementation("io.ktor:ktor-server-cio-jvm:$ktor")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktor")
    implementation("io.ktor:ktor-server-forwarded-header-jvm:$ktor")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")          // banco num arquivo só
    implementation("org.bouncycastle:bcprov-jdk18on:1.87")      // Argon2id em Java puro (sem binário nativo)
    implementation("com.google.zxing:core:3.5.4")               // QR do TOTP, em SVG
    implementation("org.slf4j:slf4j-simple:2.0.17")
    runtimeOnly("org.apache.logging.log4j:log4j-to-slf4j:2.21.1")   // o log4j do Symja sai pelo slf4j (sem o aviso do boot)
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

application { mainClass.set("tracolab.servidor.ServidorKt") }
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
