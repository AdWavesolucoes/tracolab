// Servidor de desenvolvimento: serve web/ e a API na rede local, para testar a página no
// navegador do tablet antes do APK. Não é o webservice de produção (sem limites, sem autenticação).
plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(21) }

dependencies { implementation(project(":api")) }

application { mainClass.set("tracolab.servidordev.ServidorKt") }
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
