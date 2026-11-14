#!/usr/bin/env bash
# Instala o TraçoLab como serviço systemd numa máquina Linux com Java 17+ (alternativa ao Docker).
# Roda a partir da raiz do repositório.
#
#   ferramentas/instalar-systemd.sh <destino> [endereço] [porta]
#
#   <destino>   "local" ou um destino ssh (ex.: root@maquina)
#   [endereço]  onde o serviço escuta (padrão 0.0.0.0)
#   [porta]     padrão 8098
#
# Instala o programa em /opt/tracolab-servico, os dados em /var/lib/tracolab, sob o usuário de
# sistema "tracolab", como o serviço `tracolab`. As opções (cadastro, HTTPS, proxy...) ficam em
# /etc/tracolab.env, que o script cria se não existir e nunca sobrescreve.
set -euo pipefail

[ $# -ge 1 ] || { sed -n '2,13p' "$0"; exit 2; }
DESTINO=$1; ENDERECO=${2:-0.0.0.0}; PORTA=${3:-8098}

./gradlew -q --no-daemon :servidor:installDist
PACOTE=$(mktemp -d)
trap 'rm -rf "$PACOTE"' EXIT
cp -r servidor/build/install/servidor "$PACOTE/app"
cp -r web "$PACOTE/web"
cat > "$PACOTE/tracolab.env" <<EOF
# Opções do TraçoLab (lidas pelo serviço systemd). Veja docs/hospedar.md.
TRACOLAB_CADASTRO=convite
TRACOLAB_MOTORES=2
#TRACOLAB_HTTPS=1
#TRACOLAB_PROXY=1
#TRACOLAB_MOTOR=casa
EOF
cat > "$PACOTE/tracolab.service" <<EOF
[Unit]
Description=TraçoLab
After=network-online.target
Wants=network-online.target

[Service]
User=tracolab
Group=tracolab
WorkingDirectory=/opt/tracolab-servico
EnvironmentFile=/etc/tracolab.env
Environment=TRACOLAB_ENDERECO=$ENDERECO
Environment=TRACOLAB_PORTA=$PORTA
Environment=TRACOLAB_DADOS=/var/lib/tracolab
Environment=TRACOLAB_WEB=/opt/tracolab-servico/web
# ExitOnOutOfMemoryError: sem memória, a JVM sai e o systemd sobe de novo (Restart=on-failure)
Environment="JAVA_OPTS=-Xmx256m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Djava.util.prefs.userRoot=/tmp/prefs"
ExecStart=/opt/tracolab-servico/app/bin/servidor
Restart=on-failure
RestartSec=3
# Limites (ADR 0017): heap de 256 MB + metaspace, pilhas e código da JVM cabem em 512 MB
MemoryMax=512M
TasksMax=128
LimitNOFILE=4096
# Endurecimento: a JVM só precisa de rede, de ler o programa e de escrever em /var/lib/tracolab
NoNewPrivileges=yes
ProtectSystem=strict
ReadWritePaths=/var/lib/tracolab
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
mkdir -p /opt/tracolab-servico /var/lib/tracolab
rm -rf /opt/tracolab-servico/app /opt/tracolab-servico/web
cp -r /tmp/tracolab-pacote/app /tmp/tracolab-pacote/web /opt/tracolab-servico/
chown -R root:root /opt/tracolab-servico && chmod -R a+rX /opt/tracolab-servico
chown tracolab:tracolab /var/lib/tracolab && chmod 700 /var/lib/tracolab
[ -f /etc/tracolab.env ] || { cp /tmp/tracolab-pacote/tracolab.env /etc/tracolab.env; chmod 640 /etc/tracolab.env; chown root:tracolab /etc/tracolab.env; }
cp /tmp/tracolab-pacote/tracolab.service /etc/systemd/system/
rm -rf /tmp/tracolab-pacote
systemctl daemon-reload
systemctl enable --quiet tracolab
systemctl restart tracolab
sleep 4
systemctl is-active tracolab'

if [ "$DESTINO" = local ]; then
  rm -rf /tmp/tracolab-pacote && cp -r "$PACOTE" /tmp/tracolab-pacote && bash -c "$INSTALAR"
else
  ssh "$DESTINO" 'rm -rf /tmp/tracolab-pacote'
  scp -q -r "$PACOTE" "$DESTINO:/tmp/tracolab-pacote"
  ssh "$DESTINO" "$INSTALAR"
fi
echo "TraçoLab em http://$ENDERECO:$PORTA/ — opções em /etc/tracolab.env, dados em /var/lib/tracolab"
