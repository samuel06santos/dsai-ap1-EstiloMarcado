# Seguranca e implantacao em producao (2026-10-01)

## O que e e por que existe

Esta spec define como o Estilo Marcado sera publicado no servidor de producao
do projeto e como a aplicacao sera protegida contra varreduras automaticas
(scanners), tentativas de exploracao de rotas conhecidas, forca bruta de login e
abuso de requisicoes.

O servidor ja possui **nginx** e **fail2ban** instalados e compartilhados com
outras aplicacoes. Portanto, esta spec **nao** instala nem substitui essas
ferramentas: ela descreve como configurar um novo site no nginx existente, um
novo conjunto de jails do fail2ban e como empacotar a aplicacao em imagens
Docker de producao, sem derrubar nem afetar os outros sites do mesmo servidor.

O dominio publico desta aplicacao e `estilomarcado.samuelsantos.qzz.io`. O
backend continua sendo a autoridade de autenticacao e autorizacao (conforme a
spec de autenticacao); as camadas descritas aqui atuam **antes** do backend,
cortando trafego malicioso na borda e reduzindo a superficie de ataque.

## Topologia de producao

```text
Internet
   |  HTTPS (443) / HTTP (80 -> 301)
   v
+---------------------------------------------------------------+
| Servidor host                                                 |
|                                                               |
|  nginx do host (TLS, rate limit, bloqueio de scanners, header)|
|       |                                                       |
|       |  proxy_pass http://127.0.0.1:8081  (somente loopback)  |
|       v                                                       |
|  +------------------------- Docker Compose ------------------+ |
|  |  frontend (nginx nao privilegiado, porta interna 8080)    | |
|  |       |  /  -> arquivos estaticos do Angular             | |
|  |       |  /api, /actuator -> backend:8080                 | |
|  |       v                                                   | |
|  |  backend (Spring Boot, rede interna, sem porta publicada) | |
|  |       |                                                   | |
|  |       v                                                   | |
|  |  postgres (rede interna, sem porta publicada)             | |
|  +-----------------------------------------------------------+ |
|                                                               |
|  fail2ban -> le /var/log/nginx/access.log e bane no iptables  |
+---------------------------------------------------------------+
```

Decisoes estruturais de rede:

- **Somente o frontend publica porta**, e apenas em `127.0.0.1` (loopback). O
  nginx do host e o unico ponto de entrada publico.
- **Backend e banco nao publicam portas**; sao acessados apenas pela rede
  interna do Compose.
- O backend nao deve confiar no IP recebido diretamente, pois fica atras de dois
  proxies; a aplicacao passa a usar `server.forward-headers-strategy=native` e a
  cadeia `X-Forwarded-For`/`X-Forwarded-Proto` definida pelo nginx.

## Camadas de protecao

### 1. Nginx do host (borda)

Configuracao do novo site (`deploy/nginx/`):

- TLS obrigatorio com redirecionamento 301 de HTTP para HTTPS.
- `server_tokens off` para nao revelar a versao do nginx.
- Cabecalhos de seguranca: HSTS, `X-Content-Type-Options`, `X-Frame-Options`,
  `Referrer-Policy`, `Permissions-Policy` e `Content-Security-Policy`.
- Limites por IP (`limit_req`/`limit_conn`) em tres faixas: navegacao geral,
  chamadas de API e um limite dedicado e mais rigoroso para
  `POST /api/autenticacao/sessoes` (login).
- Bloqueio silencioso (`return 444`, conexao encerrada sem resposta) para:
  - user-agents de ferramentas de varredura conhecidas;
  - caminhos tipicos de varredura e arquivos sensiveis (`.env`, `.git`,
    `wp-admin`, `phpmyadmin`, `xmlrpc.php`, extensoes `.php`/`.asp`/`.sql`,
    backups, `docker-compose.yml`, etc.).
- Tamanho maximo de corpo e timeouts de cabecalho/corpo para conter requisicoes
  malformadas.
- `/actuator/**` nao fica exposto publicamente (o health check roda por dentro
  da rede Docker).

As zonas de `limit_req_zone`/`limit_conn_zone` precisam ficar no contexto
`http`; por isso sao entregues em um arquivo separado em `conf.d/`, para nao
alterar o comportamento dos outros sites do servidor.

### 2. Fail2ban

Cada tentativa bloqueada pelo nginx e registrada no access log com status `444`.
O fail2ban le esse log e bane o IP ofensor no `iptables`. Sao definidas tres
jails:

| Jail | Gatilho | maxretry | findtime | bantime |
| --- | --- | ---: | ---: | ---: |
| `estilomarcado-scan` | qualquer requisicao com status `444` (scanner/UA suspeito) | 1 | 1h | 24h |
| `estilomarcado-probe` | 4xx em caminhos sensiveis conhecidos | 5 | 10m | 6h |
| `estilomarcado-auth` | `POST /api/autenticacao/sessoes` com `401` (login invalido) | 5 | 10m | 1h |

Observacoes:

- O banimento ocorre no `INPUT`, porque o trafego publico chega ao processo do
  nginx do host (as portas da aplicacao estao somente em loopback).
- O fail2ban **complementa** os limites ja implementados no backend
  (`LimiteRequisicoesService`), que continuam valendo dentro da aplicacao.
- Jails prontas do fail2ban (`nginx-botsearch`, `nginx-http-auth`) podem ser
  habilitadas como reforço adicional, pois sao globais do nginx; habilitar
  afeta todos os sites do servidor e por isso e opcional.

### 3. Aplicacao (backend)

- `SESSION_COOKIE_SECURE=true` para que o cookie de sessao so trafegue em HTTPS.
- `SERVER_FORWARD_HEADERS_STRATEGY=native` para que `getRemoteAddr()` reflita o
  IP real do cliente (usado por rate limit e auditoria) e os links de e-mail
  usem o esquema correto.
- `AUTH_ALLOWED_ORIGINS` e `FRONTEND_URL` restritos a
  `https://estilomarcado.samuelsantos.qzz.io` (CORS sem `*`).
- `AUTH_PASSWORD_COST` mantido em `12` (ou maior) em producao.
- `AUTH_BOOTSTRAP_ADMIN_ENABLED=true` apenas na primeira subida, para
  provisionar o primeiro administrador sem senha fixa no repositorio; depois
  volta para `false`.
- Actuator expondo apenas `health` e `info`, sem porta publica.

### 4. Docker e imagens

- **Backend:** build multi-stage (Maven + JDK 21 -> JRE 21 Alpine), executado
  como usuario sem privilegio, `MaxRAMPercentage` limitado.
- **Frontend:** build multi-stage (Node 22 -> nginx nao privilegiado em 8080),
  servindo o Angular compilado e fazendo proxy interno de `/api` e `/actuator`.
- `docker-compose.prod.yml` separado do ambiente de desenvolvimento, sem
  pgAdmin nem Mailpit, com:
  - `restart: unless-stopped`;
  - healthchecks;
  - `security_opt: no-new-privileges`;
  - limites de CPU/memoria;
  - rotacao de logs (`max-size`/`max-file`) para nao encher o disco;
  - volume nomeado para os dados do PostgreSQL.
- Segredos apenas no arquivo `deploy/.env.production` (ignorado pelo Git),
  com permissao `chmod 600`.

## Arquivos entregues

| Arquivo | Papel |
| --- | --- |
| `docker-compose.prod.yml` | Orquestracao de producao (frontend, backend, postgres). |
| `src/backend/Dockerfile` | Imagem de producao do backend. |
| `src/frontend/Dockerfile` | Imagem de producao do frontend. |
| `src/frontend/nginx/default.conf` | nginx interno do container: SPA + proxy `/api`. |
| `deploy/nginx/conf.d/estilomarcado-limits.conf` | Zonas de rate limit (contexto `http`). |
| `deploy/nginx/sites-available/estilomarcado.samuelsantos.qzz.io.conf` | Site TLS, headers, bloqueio de scanners e proxy. |
| `deploy/nginx/sites-available/estilomarcado-bootstrap-http.conf` | Vhost HTTP temporario usado apenas para emitir o primeiro certificado. |
| `deploy/fail2ban/filter.d/estilomarcado-scan.conf` | Filtro de scanners (status 444). |
| `deploy/fail2ban/filter.d/estilomarcado-probe.conf` | Filtro de varredura de caminhos sensiveis. |
| `deploy/fail2ban/filter.d/estilomarcado-auth.conf` | Filtro de falhas de login (401). |
| `deploy/fail2ban/jail.d/estilomarcado.local` | Jails do fail2ban. |
| `deploy/env.production.example` | Modelo de variaveis de ambiente de producao. |
| `deploy/README.md` | Passo a passo com os comandos de instalacao. |

## Criterios de aceitacao

- `https://estilomarcado.samuelsantos.qzz.io` abre a aplicacao com certificado
  valido e `http://` redireciona para `https://`.
- Certificado renovavel automaticamente pelo Let's Encrypt (Certbot) sem parar
  o nginx.
- Somente as portas 80/443 do host estao abertas; PostgreSQL, backend e a porta
  8081 do frontend nao sao acessiveis pela Internet.
- Requisicao a caminhos como `/.env`, `/.git/config`, `/wp-login.php` e
  `/phpmyadmin` retorna conexao encerrada (`444`) e aparece no log do nginx.
- Uma ferramenta de varredura que gere poucas requisicoes bloqueadas tem o IP
  banido pelo fail2ban e passa a nao receber resposta.
- Cinco logins invalidos em 10 minutos para o mesmo IP resultam em banimento
  temporario pelo fail2ban, alem do bloqueio por conta ja existente.
- Uma rajada de requisicoes ao login acima do limite do nginx recebe `429`.
- O cookie de sessao possui `Secure`, `HttpOnly` e `SameSite=Lax`.
- Requisicoes de API sao redirecionadas pelo container ao backend e os headers
  de seguranca aparecem na resposta das paginas.
- Os dados do PostgreSQL sobrevivem a `docker compose ... down` e
  `up` (volume nomeado).
- Nenhum segredo real fica versionado; apenas `deploy/env.production.example`.

## Fora do escopo

- Instalar nginx, fail2ban, Docker ou Certbot (ja existem no servidor do usuario).
- WAF gerenciado (Cloudflare, ModSecurity) e protecao DDoS de camada 3/4.
- Alta disponibilidade, balanceamento entre multiplos servidores e replicacao do
  banco.
- Observabilidade centralizada (Prometheus/Grafana/Loki).
- Ajustar o remetente fixo `nao-responda@estilomarcado.local` no envio de
  e-mails; antes do uso real de SMTP, esse endereco deve virar configuracao
  (`MAIL_FROM`). Fica registrado como pendencia de codigo, nao desta spec.

## Decisoes

- Reaproveitar o nginx e o fail2ban existentes do servidor, adicionando apenas
  um site e um conjunto de jails, para nao impactar outras aplicacoes.
- Terminar TLS no nginx do host e publicar a aplicacao apenas em loopback.
- Bloquear scanners com `444` (sem resposta) em vez de `403`, para nao fornecer
  informacao e economizar banda.
- Tratar o bloqueio em tres aneis complementares: nginx (rate limit e
  scanner), fail2ban (banimento por IP) e backend (regras de negocio).
- Manter as imagens o mais simples possivel, rodando como usuario sem
  privilegio, sem introduzir orquestrador externo.

## Relacao com outras specs

- Complementa a spec **Ambiente de desenvolvimento local**, capitalizando o
  `Dockerfile.dev` e o `docker-compose.yml` existentes sem altera-los.
- Depende da spec **Autenticacao e controle de acesso** para o endpoint de login
  e para as regras de bloqueio de conta; esta spec atua na borda, antes delas.
- Deve ser lida em conjunto com `deploy/README.md`, que traz os comandos
  exatos de implantacao.

## Rastreabilidade

Toda alteracao derivada desta spec deve ser commitada com o trailer:

```text
Spec: SPEC/2026-10-01-seguranca-e-implantacao-em-producao.md
```
