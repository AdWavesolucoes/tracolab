# Hospedar o TraçoLab

O TraçoLab é **um processo só** (JVM) com a página, a API de cálculo, as contas e as folhas, e
**um arquivo só** de dados (SQLite). Dá para rodar num servidor doméstico, numa placa ARM ou numa
VPS. Este guia cobre o Docker (recomendado), o systemd, as opções, o proxy com HTTPS, a cópia de
segurança e a atualização.

## 1. Docker

```bash
docker run -d --name tracolab --restart unless-stopped \
  -p 8098:8098 -v tracolab-dados:/dados \
  ghcr.io/adwavesolucoes/tracolab:latest
```

Ou com o [`docker-compose.yml`](../docker-compose.yml) do repositório: `docker compose up -d`.
A imagem existe para `linux/amd64` e `linux/arm64`. Para compilar a partir do código em vez de
baixar: `docker build -t tracolab .` (leva alguns minutos na primeira vez).

Abra `http://<máquina>:8098/`. A primeira tela é o **primeiro acesso**: a conta criada ali é a
do administrador.

## 2. Sem Docker (systemd)

Numa máquina Linux com Java 17 ou mais novo, a partir do repositório:

```bash
ferramentas/instalar-systemd.sh local            # ou root@maquina, para instalar por ssh
```

Instala o programa em `/opt/tracolab-servico`, os dados em `/var/lib/tracolab`, as opções em
`/etc/tracolab.env`, e sobe o serviço `tracolab` sob um usuário de sistema sem privilégios, com a
unit endurecida (`ProtectSystem=strict`, sem capacidades, 512 MB de memória).

## 3. Opções (variáveis de ambiente)

| Variável | Padrão | O que faz |
|---|---|---|
| `TRACOLAB_CADASTRO` | `convite` | `convite`: só com link gerado pelo administrador · `aberto`: qualquer pessoa cria conta · `fechado`: ninguém |
| `TRACOLAB_PORTA` | `8098` | Porta HTTP |
| `TRACOLAB_ENDERECO` | `0.0.0.0` | Interface em que escuta (`127.0.0.1` quando só o proxy local acessa) |
| `TRACOLAB_DADOS` | `/dados` (Docker) · `./dados` | Diretório do banco `tracolab.db` |
| `TRACOLAB_HTTPS` | — | `1` quando a instância é servida por HTTPS: o cookie de sessão ganha `Secure` |
| `TRACOLAB_PROXY` | — | `1` quando há proxy reverso na frente: confia em `X-Forwarded-For/Proto/Host` (limite de tentativas por IP e links de convite certos) |
| `TRACOLAB_MOTORES` | `2` | Cálculos simultâneos. Cada motor Symja usa memória; 1 por núcleo de CPU é um bom teto |
| `TRACOLAB_MOTOR` | — | Nome da instância, mostrado na barra da página |
| `TRACOLAB_TENTATIVAS` | `10` | Entradas erradas por usuário em 15 min antes do bloqueio (por IP, 10× isso) |
| `JAVA_OPTS` | `-Xmx256m …` | Opções da JVM. 256 MB de heap atendem uma casa ou uma equipe pequena |

A primeira conta sempre pode ser criada, seja qual for o modo de cadastro.

## 4. Na internet: proxy com HTTPS

O servidor fala só HTTP. Exposto na internet, ele fica **atrás de um proxy que faz o TLS**
(Caddy, nginx, Traefik, ou um túnel como o Cloudflare Tunnel), com `TRACOLAB_HTTPS=1` e
`TRACOLAB_PROXY=1`. Exemplo com Caddy, que emite o certificado sozinho:

```
tracolab.exemplo.com.br {
    reverse_proxy 127.0.0.1:8098
}
```

Segurança já embutida: senhas com Argon2id; sessão em cookie `HttpOnly` + `SameSite=Strict`;
CSRF barrado por `Sec-Fetch-Site`/`Origin` e pela exigência de JSON; limite de tentativas de
entrada; verificação em duas etapas por TOTP com códigos de recuperação; limites de tempo,
tamanho e recursão em cada cálculo (ADR 0017); cabeçalhos CSP, `nosniff`, `frame-ancestors
'none'`. Recomendação para instâncias públicas: cadastro por convite e 2FA no administrador.

## 5. Administração

Na página de conta, o administrador:

- **gera convites** (link válido por 7 dias, uso único) e cancela os abertos;
- **lista usuários**, torna alguém administrador, **zera o 2FA** de quem perdeu o celular e os
  códigos de recuperação, e **remove contas** (com as folhas delas).

Não há recuperação de senha por e-mail: quem esqueceu a senha pede ao administrador para remover
a conta e criar outra (exporte as folhas em `.tlab` antes, se ainda houver sessão aberta) — ou o
administrador, com acesso ao banco, remove a conta pelo painel.

## 6. Cópia de segurança

Tudo está em `tracolab.db` (mais `-wal`/`-shm` enquanto o servidor roda). O jeito seguro de
copiar com o servidor no ar é pelo próprio SQLite:

```bash
docker exec tracolab bash -c 'command -v sqlite3' >/dev/null 2>&1 \
  || echo "sem sqlite3 na imagem: pare o container antes de copiar, ou use o sqlite3 do host no volume"
sqlite3 /var/lib/docker/volumes/tracolab-dados/_data/tracolab.db ".backup '/backup/tracolab-$(date +%F).db'"
```

Com o container parado, copiar o arquivo basta. Restaurar é colocar o arquivo de volta no
diretório de dados antes de subir.

## 7. Atualizar

```bash
docker compose pull && docker compose up -d        # ou: docker pull … && docker rm -f tracolab && docker run …
```

As migrações do banco rodam sozinhas na subida. Versões seguem o [CHANGELOG](../CHANGELOG.md);
o banco de uma versão nova não deve ser aberto por uma versão antiga.

## 8. Limites por padrão

500 folhas por conta e 4 MB por folha; 2.000 regiões e 20 mil caracteres por fórmula num cálculo
(ADR 0017); sessões de 30 dias renovadas pelo uso.
