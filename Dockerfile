# syntax=docker/dockerfile:1
# TraçoLab (ADR 0019): imagem única com o servidor (Ktor + núcleo) e a página. Funciona em amd64 e
# arm64. Os dados (SQLite) ficam em /dados: monte um volume lá.
#
#   docker build -t tracolab .
#   docker run -d --name tracolab -p 8098:8098 -v tracolab-dados:/dados tracolab
#
# Todas as opções são variáveis de ambiente (ver servidor/src/main/kotlin/tracolab/servidor/Config.kt).

# o estágio de build roda na arquitetura do host (bytecode é o mesmo): sem Gradle sob emulação no CI
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY nucleo ./nucleo
COPY api ./api
COPY servidor ./servidor
COPY compat ./compat
COPY bancada ./bancada
COPY spec ./spec
RUN --mount=type=cache,target=/root/.gradle ./gradlew -q --no-daemon :servidor:installDist

FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.title="TraçoLab" \
      org.opencontainers.image.description="Caderno de cálculo de engenharia em que faltar variável não é erro" \
      org.opencontainers.image.source="https://github.com/AdWavesolucoes/tracolab" \
      org.opencontainers.image.licenses="AGPL-3.0-only"
ENV TRACOLAB_ENDERECO=0.0.0.0 \
    TRACOLAB_PORTA=8098 \
    TRACOLAB_DADOS=/dados \
    TRACOLAB_WEB=/app/web \
    TRACOLAB_CADASTRO=convite \
    TRACOLAB_MOTORES=2 \
    JAVA_OPTS="-Xmx256m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Djava.util.prefs.userRoot=/tmp/prefs"
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin tracolab \
 && mkdir -p /dados && chown tracolab:tracolab /dados
WORKDIR /app
COPY --from=build --chown=root:root /src/servidor/build/install/servidor /app/servidor
COPY --chown=root:root web /app/web
USER tracolab
VOLUME /dados
EXPOSE 8098
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${TRACOLAB_PORTA} && printf "GET /api/instancia HTTP/1.0\r\n\r\n" >&3 && head -1 <&3 | grep -q " 200 "'
ENTRYPOINT ["/app/servidor/bin/servidor"]
