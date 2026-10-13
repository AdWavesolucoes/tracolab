// log4j-api 2.21.1 com a classe LogManager trocada por uma versão compatível com Android.
// Sem ela, o Symja não inicia no Android (ver o fonte e docs/pesquisa/prova-de-conceito-kotlin-symja.md).
// O app Android deve depender do JAR deste módulo e excluir o log4j-api original.
plugins { `java-library` }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

val original by configurations.creating { isTransitive = false }

dependencies {
    compileOnly("org.apache.logging.log4j:log4j-api:2.21.1")
    original("org.apache.logging.log4j:log4j-api:2.21.1")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ original.map { zipTree(it) } }) {
        exclude("org/apache/logging/log4j/LogManager.class", "org/apache/logging/log4j/LogManager\$*.class",
                "META-INF/versions/**", "module-info.class", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    }
    from("NOTICE")
}
