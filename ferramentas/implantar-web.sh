#!/usr/bin/env bash
# Implanta a versão web de teste (servidor-dev + web/) como serviço systemd numa máquina Linux
# com Java 17+. Roda a partir da raiz do repositório.
#
#   ferramentas/implantar-web.sh <destino> <endereço> <porta> <nome-do-motor>
#
#   <destino>        "local" ou um destino ssh (ex.: root@maquina)
#   <endereço>       onde o serviço escuta (ex.: 127.0.0.1 ou o IP da rede local)
#   <nome-do-motor>  aparece na página como a máquina que está calculando
#
# Instala em /opt/tracolab-web, com o usuário de sistema "tracolab" e o serviço tracolab-web.
# Não abre nada para a internet: o acesso de fora deve passar por VPN (ex.: Tailscale).
set -euo pipefail

[ $# -eq 4 ] || { sed -n '2,13p' "$0"; exit 2; }
DESTINO=$1; ENDERECO=$2; PORTA=$3; NOME=$4

./gradlew -q --no-daemon :servidor-dev:installDist
PACOTE=$(mktemp -d)
trap 'rm -rf "$PACOTE"' EXIT
cp -r servidor-dev/build/install/servidor-dev "$PACOTE/app"
cp -r web "$PACOTE/web"
cat > "$PACOTE/tracolab-web.service" <<EOF
[Unit]
Description=TraçoLab (versão web de teste)
After=network-online.target
Wants=network-online.target

[Service]
User=tracolab
Group=tracolab
WorkingDirectory=/opt/tracolab-web
Environment=TRACOLAB_MOTOR=$NOME
Environment=TRACOLAB_MOTORES=2
# ExitOnOutOfMemoryError: sem memória, a JVM sai e o systemd sobe de novo (Restart=on-failure)
Environment="JAVA_OPTS=-Xmx256m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Djava.util.prefs.userRoot=/tmp/prefs"
ExecStart=/opt/tracolab-web/app/bin/servidor-dev $ENDERECO $PORTA
Restart=on-failure
RestartSec=3
# Limites (ADR 0017): heap de 256 MB + metaspace, pilhas e código da JVM cabem em 512 MB
MemoryMax=512M
TasksMax=128
LimitNOFILE=4096
# Endurecimento: a JVM só precisa de rede e de ler /opt/tracolab-web (nada de
# MemoryDenyWriteExecute: o JIT escreve código executável)
NoNewPrivileges=yes
ProtectSystem=strict
ProtectHome=yes
PrivateTmp=yes
PrivateDevices=yes
CapabilityBoundingSet=
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX
RestrictNamespaces=yes
RestrictRealtime=yes
RestrictSUIDSGID=yes
LockPersonality=yes
ProtectKernelTunables=yes
ProtectKernelModules=yes
ProtectKernelLogs=yes
ProtectControlGroups=yes
ProtectClock=yes
ProtectHostname=yes
SystemCallArchitectures=native
UMask=0077

[Install]
WantedBy=multi-user.target
EOF

INSTALAR='set -e
id tracolab >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin tracolab
mkdir -p /opt/tracolab-web
rm -rf /opt/tracolab-web/app /opt/tracolab-web/web
cp -r /tmp/tracolab-web-pacote/app /tmp/tracolab-web-pacote/web /opt/tracolab-web/
chown -R root:root /opt/tracolab-web && chmod -R a+rX /opt/tracolab-web
cp /tmp/tracolab-web-pacote/tracolab-web.service /etc/systemd/system/
rm -rf /tmp/tracolab-web-pacote
systemctl daemon-reload
systemctl enable --quiet tracolab-web
systemctl restart tracolab-web
sleep 4
systemctl is-active tracolab-web'

if [ "$DESTINO" = local ]; then
  rm -rf /tmp/tracolab-web-pacote && cp -r "$PACOTE" /tmp/tracolab-web-pacote && bash -c "$INSTALAR"
else
  ssh "$DESTINO" 'rm -rf /tmp/tracolab-web-pacote'
  scp -q -r "$PACOTE" "$DESTINO:/tmp/tracolab-web-pacote"
  ssh "$DESTINO" "$INSTALAR"
fi
