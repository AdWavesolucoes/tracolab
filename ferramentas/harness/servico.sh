#!/usr/bin/env bash
# Harness do serviço (ADR 0019): sobe o servidor num diretório de dados temporário e numa porta
# livre, e confere pelo HTTP, como um navegador faria, o contrato inteiro:
#
#   1. página e arquivos estáticos, cabeçalhos de segurança, 404/405
#   2. API de cálculo (/api/calcular): contrato, erros em JSON, limites
#   3. contas: primeiro acesso vira administrador, cadastro por convite, entrar/sair, senha,
#      2FA (TOTP + códigos de recuperação), limite de tentativas, CSRF
#   4. folhas por usuário: criar, listar, abrir, salvar com versão, conflito, apagar, isolamento
#
#   ferramentas/harness/servico.sh            # compila (:servidor:installDist) e roda tudo
#   ferramentas/harness/servico.sh --sem-build
#
# Precisa de: bash, curl, python3. Sai com 1 se qualquer verificação falhar.
set -uo pipefail
cd "$(dirname "$0")/../.."

BUILD=1
for a in "$@"; do case $a in --sem-build) BUILD=0;; esac; done

if [ $BUILD = 1 ]; then ./gradlew -q --no-daemon :servidor:installDist || { echo "build falhou"; exit 1; }; fi

PORTA=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
DADOS=$(mktemp -d)
LOG=$DADOS/servidor.log
trap 'kill $PID 2>/dev/null; wait $PID 2>/dev/null; rm -rf "$DADOS"' EXIT

TRACOLAB_ENDERECO=127.0.0.1 TRACOLAB_PORTA=$PORTA TRACOLAB_DADOS=$DADOS/dados TRACOLAB_WEB=web \
TRACOLAB_CADASTRO=convite TRACOLAB_MOTOR=harness TRACOLAB_TENTATIVAS=5 \
  "servidor/build/install/servidor/bin/servidor" >"$LOG" 2>&1 &
PID=$!
URL=http://127.0.0.1:$PORTA
for i in $(seq 1 60); do curl -s -o /dev/null "$URL/favicon.svg" && break; sleep 0.5; done
curl -s -o /dev/null "$URL/favicon.svg" || { echo "servidor não subiu:"; cat "$LOG"; exit 1; }

OK=0; FALHAS=0
ok()    { OK=$((OK+1)); echo "  ok    $1"; }
falha() { FALHAS=$((FALHAS+1)); echo "  FALHA $1 ${2:-}"; }
confere() { if [ "$2" = "$3" ]; then ok "$1"; else falha "$1" "(esperado '$3', veio '$2')"; fi; }
contem()  { if [[ "$2" == *"$3"* ]]; then ok "$1"; else falha "$1" "(não contém '$3': ${2:0:200})"; fi; }
json() { python3 -c 'import sys,json
d=json.load(sys.stdin);
for k in sys.argv[1].split("."):
    d = d[int(k)] if k.lstrip("-").isdigit() and isinstance(d,list) else d.get(k) if isinstance(d,dict) else None
print("" if d is None else d if not isinstance(d,(dict,list)) else json.dumps(d,ensure_ascii=False))' "$1"; }

# pedido(método, caminho, [corpo], [jarra], [cabeçalhos extras...]) → imprime "código\ncorpo"
pedido() {
  local m=$1 c=$2 corpo=${3:-} jarra=${4:-}; shift 4 2>/dev/null || shift $#
  local args=(-s -o "$DADOS/corpo" -w '%{http_code}' -X "$m" "$URL$c")
  [ -n "$corpo" ] && args+=(-H 'Content-Type: application/json' --data-binary "$corpo")
  [ -n "$jarra" ] && args+=(-b "$jarra" -c "$jarra")
  for h in "$@"; do args+=(-H "$h"); done
  curl "${args[@]}"; echo; cat "$DADOS/corpo"
}
codigo() { head -1; }
corpo()  { tail -n +2; }

echo "== 1. página, estáticos e cabeçalhos"
R=$(curl -s -D "$DADOS/h" -o /dev/null -w '%{http_code}' "$URL/")
confere "GET / responde 200" "$R" 200
contem "CSP com script-src 'self'" "$(cat "$DADOS/h")" "script-src 'self'"
contem "X-Content-Type-Options nosniff" "$(cat "$DADOS/h")" "nosniff"
contem "X-Frame-Options DENY" "$(cat "$DADOS/h")" "DENY"
confere "GET /js/folha.js 200" "$(curl -s -o /dev/null -w '%{http_code}' "$URL/js/folha.js")" 200
contem "js vem como text/javascript" "$(curl -s -I "$URL/js/folha.js" | tr -d '\r')" "text/javascript"
confere "arquivo inexistente 404" "$(curl -s -o /dev/null -w '%{http_code}' "$URL/nao-existe.txt")" 404
confere "fora da raiz web 404" "$(curl -s --path-as-is -o /dev/null -w '%{http_code}' "$URL/../build.gradle.kts")" 404
confere "PUT na página 405" "$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$URL/index.html")" 405
confere "GET /api/calcular 405" "$(curl -s -o /dev/null -w '%{http_code}' "$URL/api/calcular")" 405
LM=$(curl -s -I "$URL/" | tr -d '\r' | grep -i '^last-modified:' | cut -d' ' -f2-)
confere "If-Modified-Since devolve 304" "$(curl -s -o /dev/null -w '%{http_code}' -H "If-Modified-Since: $LM" "$URL/")" 304

CALC='{"modo":"parcial","regioes":[{"id":"r1","latex":"d\\coloneq 20\\,\\mathrm{mm}","x":40,"y":48},{"id":"r2","latex":"\\sigma\\coloneq \\frac{F}{d^2}","x":40,"y":132},{"id":"r3","latex":"\\sigma=","x":40,"y":240}]}'
calc_contrato() {   # $1 = jarra (ou vazio)
  local resp; resp=$(pedido POST /api/calcular "$CALC" "$1")
  confere "calcular responde 200" "$(echo "$resp" | codigo)" 200
  local b; b=$(echo "$resp" | corpo)
  confere "r3 fica parcial (falta F)" "$(echo "$b" | json regioes.2.tipo)" parcial
  confere "r3 lista F como livre" "$(echo "$b" | json regioes.2.livres)" '["F"]'
  confere "r1 define d" "$(echo "$b" | json regioes.0.define)" '["d"]'
  confere "resposta traz ms" "$(echo "$b" | json ms | grep -cE '^[0-9]+$')" 1
  resp=$(pedido POST /api/calcular '{' "$1")
  confere "JSON inválido → 400" "$(echo "$resp" | codigo)" 400
  confere "erro em JSON" "$(echo "$resp" | corpo | json erro)" "JSON inválido."
  resp=$(pedido POST /api/calcular '{"regioes":[{"latex":"1="}]}' "$1")
  confere "região sem id → 400" "$(echo "$resp" | codigo)" 400
  python3 -c 'print("x"*5_000_000, end="")' > "$DADOS/grande"
  local args=(-s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' --data-binary "@$DADOS/grande" "$URL/api/calcular")
  [ -n "$1" ] && args+=(-b "$1")
  confere "pedido de 5 MB → 413" "$(curl "${args[@]}")" 413
  contem "cabeçalho X-TracoLab-Motor" "$(curl -s -D - -o /dev/null ${1:+-b "$1"} -H 'Content-Type: application/json' --data-binary "$CALC" "$URL/api/calcular")" "X-TracoLab-Motor"
}

echo "== 3. contas"
J1=$DADOS/j1; J2=$DADOS/j2; J3=$DADOS/j3; JX=$DADOS/jx
R=$(pedido GET /api/instancia)
confere "instância pública: 200" "$(echo "$R" | codigo)" 200
confere "primeiro acesso ainda não feito" "$(echo "$R" | corpo | json primeiroAcesso)" True
confere "cadastro em modo convite" "$(echo "$R" | corpo | json cadastro)" convite
confere "sem sessão /api/conta → 401" "$(pedido GET /api/conta | codigo)" 401
confere "sem sessão /api/calcular → 401" "$(pedido POST /api/calcular "$CALC" | codigo)" 401
confere "sem sessão /api/folhas → 401" "$(pedido GET /api/folhas | codigo)" 401

confere "senha curta recusada (400)" "$(pedido POST /api/conta/cadastrar '{"usuario":"lucas","senha":"curta"}' "$J1" | codigo)" 400
confere "usuário inválido recusado (400)" "$(pedido POST /api/conta/cadastrar '{"usuario":"Lu cas!","senha":"senha-bem-comprida-1"}' "$J1" | codigo)" 400
R=$(pedido POST /api/conta/cadastrar '{"usuario":"lucas","senha":"senha-bem-comprida-1"}' "$J1")
confere "primeira conta criada sem convite (201)" "$(echo "$R" | codigo)" 201
R=$(pedido GET /api/conta "" "$J1")
confere "primeira conta é administradora" "$(echo "$R" | corpo | json admin)" True
confere "nome do usuário na conta" "$(echo "$R" | corpo | json usuario)" lucas
confere "primeiro acesso concluído" "$(pedido GET /api/instancia | corpo | json primeiroAcesso)" False
confere "segunda conta sem convite → 403" "$(pedido POST /api/conta/cadastrar '{"usuario":"ana","senha":"senha-bem-comprida-2"}' "$J2" | codigo)" 403
confere "convite errado → 403" "$(pedido POST /api/conta/cadastrar '{"usuario":"ana","senha":"senha-bem-comprida-2","convite":"xyz"}' "$J2" | codigo)" 403
confere "convite por quem não é admin → 401/403" "$(pedido POST /api/admin/convites '{}' "$J2" | codigo | grep -cE '^40[13]$')" 1
R=$(pedido POST /api/admin/convites '{}' "$J1")
confere "admin cria convite (201)" "$(echo "$R" | codigo)" 201
CONV=$(echo "$R" | corpo | json codigo)
contem "convite vem com link" "$(echo "$R" | corpo | json link)" "convite="
confere "cadastro com convite (201)" "$(pedido POST /api/conta/cadastrar "{\"usuario\":\"ana\",\"senha\":\"senha-bem-comprida-2\",\"convite\":\"$CONV\"}" "$J2" | codigo)" 201
confere "convite não vale duas vezes (403)" "$(pedido POST /api/conta/cadastrar "{\"usuario\":\"bia\",\"senha\":\"senha-bem-comprida-3\",\"convite\":\"$CONV\"}" "$J3" | codigo)" 403
confere "usuário repetido → 409" "$(pedido POST /api/conta/cadastrar '{"usuario":"ana","senha":"senha-bem-comprida-2","convite":"qualquer"}' "$J3" | codigo | grep -cE '^(409|403)$')" 1
confere "ana não é administradora" "$(pedido GET /api/conta "" "$J2" | corpo | json admin)" False
confere "ana não lista usuários (403)" "$(pedido GET /api/admin/usuarios "" "$J2" | codigo)" 403
confere "admin lista 2 usuários" "$(pedido GET /api/admin/usuarios "" "$J1" | corpo | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))')" 2

confere "sair → 204" "$(pedido POST /api/conta/sair "" "$J2" | codigo)" 204
confere "depois de sair, conta → 401" "$(pedido GET /api/conta "" "$J2" | codigo)" 401
confere "senha errada → 401" "$(pedido POST /api/conta/entrar '{"usuario":"ana","senha":"errada-errada-errada"}' "$J2" | codigo)" 401
confere "usuário inexistente → 401 (mesma resposta)" "$(pedido POST /api/conta/entrar '{"usuario":"ninguem","senha":"errada-errada-errada"}' "$J2" | codigo)" 401
R=$(pedido POST /api/conta/entrar '{"usuario":"ana","senha":"senha-bem-comprida-2"}' "$J2")
confere "entrar certo → 200" "$(echo "$R" | codigo)" 200
confere "sem 2FA entra direto" "$(echo "$R" | corpo | json ok)" True
contem "cookie de sessão HttpOnly" "$(cat "$J2")" "#HttpOnly_"
confere "trocar senha com atual errada → 401/400" "$(pedido POST /api/conta/senha '{"atual":"x","nova":"outra-senha-comprida-9"}' "$J2" | codigo | grep -cE '^40[01]$')" 1
confere "trocar senha → 204" "$(pedido POST /api/conta/senha '{"atual":"senha-bem-comprida-2","nova":"outra-senha-comprida-9"}' "$J2" | codigo)" 204
confere "sessão continua válida após trocar senha" "$(pedido GET /api/conta "" "$J2" | codigo)" 200
pedido POST /api/conta/sair "" "$J2" >/dev/null
confere "senha antiga não entra mais" "$(pedido POST /api/conta/entrar '{"usuario":"ana","senha":"senha-bem-comprida-2"}' "$J2" | codigo)" 401
confere "senha nova entra" "$(pedido POST /api/conta/entrar '{"usuario":"ana","senha":"outra-senha-comprida-9"}' "$J2" | codigo)" 200

echo "== 3b. CSRF"
confere "POST sem Content-Type JSON → 415" "$(curl -s -o /dev/null -w '%{http_code}' -b "$J2" -X POST --data 'a=1' "$URL/api/conta/sair")" 415
confere "POST de outro site (Sec-Fetch-Site: cross-site) → 403" "$(pedido POST /api/conta/sair "" "$J2" "Sec-Fetch-Site: cross-site" | codigo)" 403
confere "POST com Origin de outro host → 403" "$(pedido POST /api/conta/sair "" "$J2" "Origin: https://malicioso.example" | codigo)" 403
confere "mesma sessão continua (sair não executou)" "$(pedido GET /api/conta "" "$J2" | codigo)" 200

echo "== 3c. 2FA"
totp() { python3 - "$1" "${2:-0}" <<'EOF'
import sys, hmac, hashlib, base64, struct, time
s = sys.argv[1]; s += "=" * (-len(s) % 8)
chave = base64.b32decode(s.upper())
passo = int(time.time() // 30) + int(sys.argv[2])
h = hmac.new(chave, struct.pack(">Q", passo), hashlib.sha1).digest()
o = h[-1] & 15
print("%06d" % ((struct.unpack(">I", h[o:o+4])[0] & 0x7fffffff) % 1000000))
EOF
}
R=$(pedido POST /api/conta/2fa/preparar '{}' "$J2")
confere "preparar 2FA → 200" "$(echo "$R" | codigo)" 200
SEG=$(echo "$R" | corpo | json segredo)
contem "otpauth com o nome do usuário" "$(echo "$R" | corpo | json otpauth)" "otpauth://totp/"
contem "QR em SVG" "$(echo "$R" | corpo | json qr)" "<svg"
confere "ainda sem 2FA ativo" "$(pedido GET /api/conta "" "$J2" | corpo | json doisFatores)" False
confere "ativar com código errado → 400" "$(pedido POST /api/conta/2fa/ativar '{"codigo":"000000"}' "$J2" | codigo)" 400
R=$(pedido POST /api/conta/2fa/ativar "{\"codigo\":\"$(totp "$SEG")\"}" "$J2")
confere "ativar com código certo → 200" "$(echo "$R" | codigo)" 200
NREC=$(echo "$R" | corpo | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["codigosRecuperacao"]))')
confere "10 códigos de recuperação" "$NREC" 10
REC1=$(echo "$R" | corpo | json codigosRecuperacao.0)
confere "2FA ativo na conta" "$(pedido GET /api/conta "" "$J2" | corpo | json doisFatores)" True
pedido POST /api/conta/sair "" "$J2" >/dev/null
R=$(pedido POST /api/conta/entrar '{"usuario":"ana","senha":"outra-senha-comprida-9"}' "$J2")
confere "entrar pede 2FA" "$(echo "$R" | corpo | json doisFatores)" True
confere "sessão pendente não acessa a conta (401)" "$(pedido GET /api/conta "" "$J2" | codigo)" 401
confere "sessão pendente não calcula (401)" "$(pedido POST /api/calcular "$CALC" "$J2" | codigo)" 401
confere "código TOTP errado → 401" "$(pedido POST /api/conta/entrar/2fa '{"codigo":"000000"}' "$J2" | codigo)" 401
confere "código TOTP certo → 200" "$(pedido POST /api/conta/entrar/2fa "{\"codigo\":\"$(totp "$SEG")\"}" "$J2" | codigo)" 200
confere "conta acessível depois do 2FA" "$(pedido GET /api/conta "" "$J2" | codigo)" 200
pedido POST /api/conta/sair "" "$J2" >/dev/null
pedido POST /api/conta/entrar '{"usuario":"ana","senha":"outra-senha-comprida-9"}' "$J2" >/dev/null
confere "código de recuperação entra" "$(pedido POST /api/conta/entrar/2fa "{\"codigo\":\"$REC1\"}" "$J2" | codigo)" 200
pedido POST /api/conta/sair "" "$J2" >/dev/null
pedido POST /api/conta/entrar '{"usuario":"ana","senha":"outra-senha-comprida-9"}' "$J2" >/dev/null
confere "código de recuperação não vale duas vezes" "$(pedido POST /api/conta/entrar/2fa "{\"codigo\":\"$REC1\"}" "$J2" | codigo)" 401
pedido POST /api/conta/entrar/2fa "{\"codigo\":\"$(totp "$SEG")\"}" "$J2" >/dev/null
confere "desativar 2FA sem senha → 400/401" "$(pedido POST /api/conta/2fa/desativar '{"senha":"x","codigo":"000000"}' "$J2" | codigo | grep -cE '^40[01]$')" 1
confere "desativar 2FA → 204" "$(pedido POST /api/conta/2fa/desativar "{\"senha\":\"outra-senha-comprida-9\",\"codigo\":\"$(totp "$SEG")\"}" "$J2" | codigo)" 204
confere "2FA desligado" "$(pedido GET /api/conta "" "$J2" | corpo | json doisFatores)" False

echo "== 3d. limite de tentativas (TRACOLAB_TENTATIVAS=5)"
for i in 1 2 3 4 5; do pedido POST /api/conta/entrar '{"usuario":"lucas","senha":"errada-errada-errada"}' "$JX" >/dev/null; done
confere "6ª tentativa → 429" "$(pedido POST /api/conta/entrar '{"usuario":"lucas","senha":"errada-errada-errada"}' "$JX" | codigo)" 429
confere "até a senha certa é barrada enquanto bloqueado" "$(pedido POST /api/conta/entrar '{"usuario":"lucas","senha":"senha-bem-comprida-1"}' "$JX" | codigo)" 429
confere "sessão já aberta do lucas continua" "$(pedido GET /api/conta "" "$J1" | codigo)" 200

echo "== 4. folhas"
confere "lista vazia no começo" "$(pedido GET /api/folhas "" "$J1" | corpo)" "[]"
FOLHA='{"nome":"Barra tracionada","conteudo":{"modo":"parcial","ordem":false,"regioes":[{"id":"r1","latex":"d\\coloneq 20\\,\\mathrm{mm}","unidade":"","x":40,"y":48}]}}'
R=$(pedido POST /api/folhas "$FOLHA" "$J1")
confere "criar folha → 201" "$(echo "$R" | codigo)" 201
FID=$(echo "$R" | corpo | json id)
confere "folha nova tem versão 1" "$(echo "$R" | corpo | json versao)" 1
R=$(pedido GET "/api/folhas/$FID" "" "$J1")
confere "abrir folha → 200" "$(echo "$R" | codigo)" 200
confere "conteúdo volta igual" "$(echo "$R" | corpo | json conteudo.regioes.0.latex)" 'd\coloneq 20\,\mathrm{mm}'
confere "nome volta" "$(echo "$R" | corpo | json nome)" "Barra tracionada"
confere "lista com 1 folha" "$(pedido GET /api/folhas "" "$J1" | corpo | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))')" 1
R=$(pedido PUT "/api/folhas/$FID" '{"nome":"Barra","conteudo":{"modo":"parcial","regioes":[]},"versao":1}' "$J1")
confere "salvar com a versão certa → 200" "$(echo "$R" | codigo)" 200
confere "versão sobe para 2" "$(echo "$R" | corpo | json versao)" 2
R=$(pedido PUT "/api/folhas/$FID" '{"nome":"Barra velha","conteudo":{"modo":"parcial","regioes":[]},"versao":1}' "$J1")
confere "salvar com versão velha → 409" "$(echo "$R" | codigo)" 409
confere "409 devolve a versão atual" "$(echo "$R" | corpo | json versao)" 2
confere "409 devolve o nome atual" "$(echo "$R" | corpo | json nome)" "Barra"
confere "ana não vê a folha do lucas (404)" "$(pedido GET "/api/folhas/$FID" "" "$J2" | codigo)" 404
confere "ana não salva a folha do lucas (404)" "$(pedido PUT "/api/folhas/$FID" '{"nome":"x","conteudo":{"regioes":[]},"versao":2}' "$J2" | codigo)" 404
confere "ana não apaga a folha do lucas (404)" "$(pedido DELETE "/api/folhas/$FID" "" "$J2" | codigo)" 404
confere "lista da ana continua vazia" "$(pedido GET /api/folhas "" "$J2" | corpo)" "[]"
confere "conteúdo que não é objeto → 400" "$(pedido POST /api/folhas '{"nome":"x","conteudo":"texto"}' "$J1" | codigo)" 400
confere "folha sem nome ganha nome padrão" "$(pedido POST /api/folhas '{"conteudo":{"regioes":[]}}' "$J1" | corpo | json nome)" "Folha sem título"
confere "apagar → 204" "$(pedido DELETE "/api/folhas/$FID" "" "$J1" | codigo)" 204
confere "apagada não abre (404)" "$(pedido GET "/api/folhas/$FID" "" "$J1" | codigo)" 404

echo "== 2. API de cálculo (com sessão)"
calc_contrato "$J1"

echo "== 5. administração"
AID=$(pedido GET /api/admin/usuarios "" "$J1" | corpo | python3 -c 'import sys,json; print([u for u in json.load(sys.stdin) if u["usuario"]=="ana"][0]["id"])')
confere "admin não remove a si mesmo (400)" "$(pedido DELETE "/api/admin/usuarios/$(pedido GET /api/conta "" "$J1" | corpo | json id)" "" "$J1" | codigo)" 400
confere "admin apaga a ana → 204" "$(pedido DELETE "/api/admin/usuarios/$AID" "" "$J1" | codigo)" 204
confere "sessão da ana morre (401)" "$(pedido GET /api/conta "" "$J2" | codigo)" 401
confere "ana não entra mais" "$(pedido POST /api/conta/entrar '{"usuario":"ana","senha":"outra-senha-comprida-9"}' "$J3" | codigo)" 401

echo "== 6. reinício preserva os dados"
kill $PID; wait $PID 2>/dev/null
TRACOLAB_ENDERECO=127.0.0.1 TRACOLAB_PORTA=$PORTA TRACOLAB_DADOS=$DADOS/dados TRACOLAB_WEB=web TRACOLAB_CADASTRO=convite \
  "servidor/build/install/servidor/bin/servidor" >>"$LOG" 2>&1 &
PID=$!
for i in $(seq 1 60); do curl -s -o /dev/null "$URL/favicon.svg" && break; sleep 0.5; done
confere "depois de reiniciar, sessão do lucas continua" "$(pedido GET /api/conta "" "$J1" | codigo)" 200
confere "primeiro acesso continua concluído" "$(pedido GET /api/instancia | corpo | json primeiroAcesso)" False
confere "o lucas ainda entra" "$(pedido POST /api/conta/entrar '{"usuario":"lucas","senha":"senha-bem-comprida-1"}' "$J3" | codigo)" 200

echo
echo "ok=$OK falhas=$FALHAS"
if [ $FALHAS != 0 ]; then echo "--- log do servidor:"; tail -40 "$LOG"; exit 1; fi
