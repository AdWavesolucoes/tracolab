#!/usr/bin/env python3
"""Entradas hostis ou acidentais contra um servidor-dev do TraçoLab (ADR 0017).

Mede, para cada caso, o tempo de resposta, o código HTTP e se o servidor continuou vivo
(um "2+2=" logo depois). Só biblioteca padrão.

    python3 -I ferramentas/robustez/hostis.py http://127.0.0.1:8123 [--reiniciar "comando"] [--so c01,c05]

--reiniciar: comando de shell que sobe o servidor de novo quando ele morre ou trava (a medição
continua nos casos seguintes). NUNCA aponte para a produção nem para o endereço público.
"""
import json
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

URL = sys.argv[1].rstrip("/") if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else "http://127.0.0.1:8123"
ARGS = sys.argv[1:]
REINICIAR = ARGS[ARGS.index("--reiniciar") + 1] if "--reiniciar" in ARGS else None
SO = set(ARGS[ARGS.index("--so") + 1].split(",")) if "--so" in ARGS else None
TEMPO = 40  # s: acima disso conta como travado
HOST = urllib.parse.urlparse(URL).hostname
PORTA = urllib.parse.urlparse(URL).port


def pedido(*latex, y0=10):
    return {"modo": "parcial", "regioes": [{"id": f"r{k}", "latex": l, "x": 40, "y": y0 + 60 * k} for k, l in enumerate(latex)]}


def post(caminho, corpo, tempo=TEMPO, tipo="application/json"):
    dados = corpo if isinstance(corpo, bytes) else json.dumps(corpo).encode()
    req = urllib.request.Request(URL + caminho, data=dados, method="POST", headers={"Content-Type": tipo})
    return abrir(req, tempo)


def metodo(m, caminho, tempo=10):
    return abrir(urllib.request.Request(URL + caminho, method=m), tempo)


def abrir(req, tempo):
    t0 = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=tempo) as r:
            corpo = r.read()
            return r.status, corpo, time.monotonic() - t0, dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read(), time.monotonic() - t0, dict(e.headers)
    except Exception as e:  # tempo esgotado, conexão recusada/fechada
        return None, repr(e).encode()[:200], time.monotonic() - t0, {}


def vivo():
    st, corpo, dt, _ = post("/api/calcular", pedido("2+2="), tempo=8)
    return st == 200 and b'"resultado":"4"' in corpo, dt


def bruto(dados, tempo=10):
    """Manda bytes crus pelo socket e devolve a primeira linha da resposta."""
    t0 = time.monotonic()
    try:
        s = socket.create_connection((HOST, PORTA), timeout=tempo)
        s.sendall(dados)
        r = s.recv(200)
        s.close()
        return r.split(b"\r\n")[0].decode(errors="replace"), time.monotonic() - t0
    except Exception as e:
        return repr(e)[:80], time.monotonic() - t0


def resumo(st, corpo):
    try:
        j = json.loads(corpo)
        regs = j.get("regioes", [])
        estados = {}
        for o in regs:
            estados[o.get("tipo")] = estados.get(o.get("tipo"), 0) + 1
        msgs = [o.get("msg") for o in regs if o.get("msg")]
        maior = max((len(o.get("resultado") or "") for o in regs), default=0)
        return f"{estados} {('msg: ' + msgs[0][:70]) if msgs else ''} {'resultado de %d caracteres' % maior if maior > 2000 else ''}".strip()
    except Exception:
        return corpo[:90].decode(errors="replace").replace("\n", " ")


# ------------------------------------------------------------------ casos

def potencia_fim():
    return pedido("x\\coloneq10^{10^{10}}", "x=")


CASOS = [
    ("c01", "torre 10^10^10 numa definição", lambda: post("/api/calcular", pedido("x\\coloneq10^{10^{10}}", "x="))),
    ("c02", "9^9^9^9 avaliado", lambda: post("/api/calcular", pedido("9^{9^{9^{9}}}="))),
    ("c03", "10^1000000 avaliado", lambda: post("/api/calcular", pedido("10^{1000000}="))),
    ("c04", "10^400 avaliado (estoura o double)", lambda: post("/api/calcular", pedido("10^{400}=", "2+2="))),
    ("c05", "recursão infinita f(x) := f(x)", lambda: post("/api/calcular", pedido("f\\left(x\\right)\\coloneq f\\left(x\\right)", "f\\left(1\\right)=", "2+2="))),
    ("c06", "recursão sem caso base n·f(n−1)", lambda: post("/api/calcular", pedido("f\\left(n\\right)\\coloneq n\\cdot f\\left(n-1\\right)", "f\\left(5\\right)=", "2+2="))),
    ("c07", "faixa de 200 mil valores avaliada", lambda: post("/api/calcular", pedido("i\\coloneq0..199999", "i^2="))),
    ("c08", "faixa de 1 bilhão", lambda: post("/api/calcular", pedido("i\\coloneq0..1000000000", "i="))),
    ("c09", "(a+b+c+d)^40 simbólico", lambda: post("/api/calcular", pedido("\\left(a+b+c+d\\right)^{40}\\to"))),
    ("c10", "(x+1)^100000 simbólico", lambda: post("/api/calcular", pedido("\\left(x+1\\right)^{100000}\\to"))),
    ("c11", "5000 parênteses aninhados", lambda: post("/api/calcular", pedido("\\left(" * 5000 + "1" + "\\right)" * 5000 + "="))),
    ("c12", "20000 chaves aninhadas", lambda: post("/api/calcular", pedido("{" * 20000 + "1" + "}" * 20000 + "="))),
    ("c13", "2000 frações aninhadas", lambda: post("/api/calcular", pedido("\\frac{1}{" * 2000 + "2" + "}" * 2000 + "="))),
    ("c14", "soma de 300 mil termos (1,2 MB)", lambda: post("/api/calcular", pedido("1+" * 300000 + "1="))),
    ("c15", "5000 regiões encadeadas", lambda: post("/api/calcular", pedido(*(["x_{0}\\coloneq1"] + [f"x_{{{k}}}\\coloneq x_{{{k-1}}}+1" for k in range(1, 5000)] + ["x_{4999}="])))),
    ("c16", "50 mil regiões 1=", lambda: post("/api/calcular", pedido(*(["1="] * 50000)))),
    ("c17", "nome de 1 milhão de letras", lambda: post("/api/calcular", pedido("a" * 1000000 + "="))),
    ("c18", "JSON malformado", lambda: post("/api/calcular", b"{")),
    ("c19", "regioes não é lista", lambda: post("/api/calcular", {"regioes": 5})),
    ("c20", "região sem id", lambda: post("/api/calcular", {"regioes": [{"latex": "1="}]})),
    ("c21", "corpo de 60 MB", lambda: post("/api/calcular", b'{"regioes":[{"id":"a","latex":"' + b"1" * 60_000_000 + b'="}]}')),
    ("c22", "HEAD /", lambda: metodo("HEAD", "/")),
    ("c23", "OPTIONS /", lambda: metodo("OPTIONS", "/")),
    ("c24", "PUT /index.html", lambda: abrir(urllib.request.Request(URL + "/index.html", data=b"x", method="PUT"), 10)),
    ("c25", "GET /../../etc/passwd (cru)", lambda: (lambda r: (None if r[0].startswith(("ConnectionResetError", "TimeoutError", "socket")) else int(r[0].split()[1]) if r[0].startswith("HTTP") else None, r[0].encode(), r[1], {}))(bruto(b"GET /../../etc/passwd HTTP/1.1\r\nHost: x\r\n\r\n"))),
    ("c26", "GET /%2e%2e/%2e%2e/etc/passwd", lambda: metodo("GET", "/%2e%2e/%2e%2e/etc/passwd")),
    ("c27", "GET com %00 no caminho", lambda: metodo("GET", "/index.html%00.js")),
    ("c28", "GET de diretório /vendor/", lambda: metodo("GET", "/vendor/")),
]


def corpo_lento():
    """Um cliente manda o cabeçalho e só 10 de 1000 bytes do corpo; mede um 2+2= de outro cliente."""
    s = socket.create_connection((HOST, PORTA), timeout=30)
    s.sendall(b"POST /api/calcular HTTP/1.1\r\nHost: x\r\nContent-Length: 1000\r\n\r\n0123456789")
    time.sleep(0.5)
    ok, dt = vivo()
    s.close()
    return (200 if ok else None), f"2+2= de outro cliente: {'ok' if ok else 'NÃO respondeu'}".encode(), dt, {}


def calculo_lento_bloqueia():
    """Enquanto um cálculo pesado roda, quanto demora um 2+2= de outro cliente?"""
    import threading
    t = threading.Thread(target=lambda: post("/api/calcular", pedido("\\left(a+b+c+d+e\\right)^{40}\\to")), daemon=True)
    t.start()
    time.sleep(0.3)
    ok, dt = vivo()
    t.join(TEMPO)
    return (200 if ok else None), f"2+2= durante cálculo pesado: {'ok' if ok else 'NÃO respondeu'}".encode(), dt, {}


CASOS += [("c29", "corpo lento (slowloris) prende o servidor?", corpo_lento),
          ("c30", "cálculo pesado bloqueia os outros?", calculo_lento_bloqueia)]


def reiniciar():
    if not REINICIAR:
        return False
    subprocess.run(REINICIAR, shell=True)
    for _ in range(80):
        if vivo()[0]:
            return True
        time.sleep(0.5)
    return False


print(f"| caso | entrada | HTTP | tempo | resposta | servidor depois |\n|---|---|---|---|---|---|")
for cid, nome, fn in CASOS:
    if SO and cid not in SO:
        continue
    st, corpo, dt, cab = fn()
    ok, dtv = vivo()
    depois = f"vivo ({dtv:.1f} s)" if ok else "**MORTO/TRAVADO**"
    if not ok and reiniciar():
        depois += " → reiniciado"
    print(f"| {cid} | {nome} | {st if st is not None else '—'} | {dt:.1f} s | {resumo(st, corpo)[:110]} | {depois} |", flush=True)
