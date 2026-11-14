#!/usr/bin/env bash
# Testes de interface (Playwright) contra um servidor de teste limpo: sobe o servidor num
# diretório de dados temporário com cadastro aberto, roda cada roteiro de ferramentas/teste-ui e
# resume. Precisa do Chromium do Playwright em CHROME (ou acha o do cache do Playwright).
#
#   ferramentas/harness/interface.sh              # compila e roda todos
#   ferramentas/harness/interface.sh --sem-build fluxo documento   # só esses roteiros
set -uo pipefail
cd "$(dirname "$0")/../.."

BUILD=1; ROTEIROS=()
for a in "$@"; do case $a in --sem-build) BUILD=0;; *) ROTEIROS+=("$a");; esac; done
[ ${#ROTEIROS[@]} = 0 ] && ROTEIROS=(conta fluxo unidade-falta unidades matrizes verificacoes graficos documento exploratorio)

CHROME=${CHROME:-$(ls -d ~/.cache/ms-playwright/chromium-*/chrome-linux64/chrome 2>/dev/null | sort -V | tail -1)}
[ -x "$CHROME" ] || { echo "Chromium não encontrado: defina CHROME=/caminho/do/chrome"; exit 2; }
export CHROME

if [ $BUILD = 1 ]; then ./gradlew -q --no-daemon :servidor:installDist || { echo "build falhou"; exit 1; }; fi
[ -d ferramentas/teste-ui/node_modules ] || (cd ferramentas/teste-ui && npm ci --silent)

PORTA=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
DADOS=$(mktemp -d)
trap 'kill $PID 2>/dev/null; wait $PID 2>/dev/null; rm -rf "$DADOS"' EXIT
TRACOLAB_ENDERECO=127.0.0.1 TRACOLAB_PORTA=$PORTA TRACOLAB_DADOS=$DADOS/dados TRACOLAB_WEB=web TRACOLAB_CADASTRO=aberto TRACOLAB_MOTOR=teste-ui \
  servidor/build/install/servidor/bin/servidor >"$DADOS/servidor.log" 2>&1 &
PID=$!
URL=http://127.0.0.1:$PORTA/
for i in $(seq 1 60); do curl -s -o /dev/null "${URL}favicon.svg" && break; sleep 0.5; done
curl -s -o /dev/null "${URL}favicon.svg" || { echo "servidor não subiu:"; cat "$DADOS/servidor.log"; exit 1; }

FALHOU=0
for r in "${ROTEIROS[@]}"; do
  echo "== $r"
  (cd ferramentas/teste-ui && timeout 600 node "$r.mjs" "$URL" ${PRINTS:-} 2>&1 | grep -v "^  ok" ) || FALHOU=1
done
if [ $FALHOU != 0 ]; then echo "--- log do servidor:"; tail -30 "$DADOS/servidor.log"; fi
exit $FALHOU
