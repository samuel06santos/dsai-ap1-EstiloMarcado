# Implantacao em producao — Estilo Marcado

Guia operacional para publicar a aplicacao em
`https://estilomarcado.samuelsantos.qzz.io` usando o **nginx** e o **fail2ban**
ja instalados no servidor, com a aplicacao empacotada em Docker.

Pressupostos:

- Servidor Linux (Ubuntu/Debian) com `docker`, `nginx`, `fail2ban` e
  `certbot` instalados.
- O repositorio foi clonado em, por exemplo, `/opt/estilo-marcado`.
- As portas 80 e 443 estao liberadas; as demais permanecem fechadas.

---

## 1. DNS

Crie um registro **A** para o subdominio apontando para o IP publico do servidor:

```text
estilomarcado.samuelsantos.qzz.io.  A  <IP-DO-SERVIDOR>
```

Confirme a propagacao antes de emitir o certificado:

```bash
dig +short estilomarcado.samuelsantos.qzz.io
```

---

## 2. Codigo e variaveis de ambiente

```bash
cd /opt
git clone <URL-DO-REPOSITORIO> estilo-marcado
cd estilo-marcado

cp deploy/env.production.example deploy/.env.production
chmod 600 deploy/.env.production
nano deploy/.env.production   # preencha senha do banco, SMTP etc.
```

Gere uma senha forte para o banco, se quiser:

```bash
openssl rand -base64 32
```

---

## 3. Subir os containers de producao

```bash
docker compose --env-file deploy/.env.production -f docker-compose.prod.yml up -d --build
docker compose --env-file deploy/.env.production -f docker-compose.prod.yml ps
```

Conferencias:

```bash
# O frontend deve responder no loopback.
curl -fsS http://127.0.0.1:8081/healthz

# O backend deve reportar UP (via rede interna do Compose).
docker compose --env-file deploy/.env.production -f docker-compose.prod.yml \
  exec backend wget -qO- http://127.0.0.1:8080/actuator/health
```

Ajuste `FRONTEND_BIND` no arquivo `.env.production` se a porta 8081 ja estiver
em uso no servidor.

### Administrador configurado pelo ambiente

Defina `AUTH_BOOTSTRAP_ADMIN_ENABLED=true` e `AUTH_BOOTSTRAP_ADMIN_UNIT_ID` no
`.env.production`. Mantenha `AUTH_BOOTSTRAP_ADMIN_EMAIL` para o administrador
inicial e preencha `AUTH_ADDITIONAL_ADMIN_EMAIL` com o novo e-mail. O nome de
uma conta nova pode ser definido em `AUTH_ADDITIONAL_ADMIN_NAME`. O perfil
`ADMINISTRADOR` vale para essa unidade, conforme o modelo de permissoes do
projeto. Use o ID de uma unidade existente; com `0`, o backend localiza ou
cria a unidade pelo nome definido em `AUTH_BOOTSTRAP_ADMIN_UNIT_NAME`.

Ao recriar o backend, uma conta nova recebe convite por e-mail. Uma conta de
cliente ja ativa e com e-mail verificado no Firebase e promovida, mesmo que a
unidade ja tenha outro administrador; as sessoes antigas sao encerradas. Uma
conta pendente ou vinculada a outro perfil interno exige resolucao manual.
Falhas no provisionamento sao registradas no backend sem interromper o login;
confira os logs e o perfil da conta apos a subida. O processo e idempotente
para o mesmo e-mail e unidade:

```bash
docker compose --env-file deploy/.env.production -f docker-compose.prod.yml up -d backend
```

Depois volte para `false` e recrie o backend:

```bash
docker compose --env-file deploy/.env.production -f docker-compose.prod.yml up -d backend
```

---

## 4. Nginx do host

Instale os arquivos de configuracao (as zones de rate limit ficam no contexto
`http`, em `conf.d`):

```bash
sudo mkdir -p /var/www/certbot

sudo cp deploy/nginx/conf.d/estilomarcado-limits.conf \
        /etc/nginx/conf.d/estilomarcado-limits.conf
sudo cp deploy/nginx/proxy_params_estilomarcado.conf \
        /etc/nginx/proxy_params_estilomarcado.conf

sudo cp deploy/nginx/sites-available/estilomarcado.samuelsantos.qzz.io.conf \
        /etc/nginx/sites-available/
sudo cp deploy/nginx/sites-available/estilomarcado-bootstrap-http.conf \
        /etc/nginx/sites-available/
```

Confirme que o `nginx.conf` inclui `conf.d` e `sites-enabled` dentro do bloco
`http` (padrao no Ubuntu/Debian):

```bash
grep -nE 'include|conf.d|sites-enabled' /etc/nginx/nginx.conf
```

Valide apenas o arquivo de limites (o site TLS ainda nao tem certificado):

```bash
sudo nginx -t
```

### 4.1 Emitir o certificado

```bash
# Habilita somente o vhost HTTP temporario.
sudo ln -s /etc/nginx/sites-available/estilomarcado-bootstrap-http.conf \
           /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# Emite o certificado (nao instala vhost; apenas cria os arquivos).
sudo certbot certonly --webroot -w /var/www/certbot \
  -d estilomarcado.samuelsantos.qzz.io \
  --email <SEU-EMAIL> --agree-tos --no-eff-email
```

### 4.2 Ativar o site definitivo

```bash
# Remove o temporario e habilita o site com TLS + seguranca.
sudo rm -f /etc/nginx/sites-enabled/estilomarcado-bootstrap-http.conf
sudo ln -s /etc/nginx/sites-available/estilomarcado.samuelsantos.qzz.io.conf \
           /etc/nginx/sites-enabled/

sudo nginx -t && sudo systemctl reload nginx
```

Renovacao automatica (o pacote do Certbot ja costuma instalar o timer):

```bash
sudo systemctl status certbot.timer
sudo certbot renew --dry-run
sudo systemctl reload nginx   # hook opcional, se nao houver um automatico
```

---

## 5. Fail2ban

Instale os filtros e o jail:

```bash
sudo cp deploy/fail2ban/filter.d/estilomarcado-*.conf /etc/fail2ban/filter.d/
sudo cp deploy/fail2ban/jail.d/estilomarcado.local  /etc/fail2ban/jail.d/
```

Valide os filtros contra o log real antes de recarregar:

```bash
sudo fail2ban-regex /var/log/nginx/access.log /etc/fail2ban/filter.d/estilomarcado-scan.conf
sudo fail2ban-regex /var/log/nginx/access.log /etc/fail2ban/filter.d/estilomarcado-auth.conf
sudo fail2ban-regex /var/log/nginx/access.log /etc/fail2ban/filter.d/estilomarcado-probe.conf
```

Se o log do nginx estiver comprimido ou em outro caminho, ajuste `logpath` no
`jail.d/estilomarcado.local`.

Aplique e acompanhe:

```bash
sudo fail2ban-client reload
sudo fail2ban-client status
sudo fail2ban-client status estilomarcado-scan
sudo fail2ban-client status estilomarcado-auth
```

Desbanir um IP manualmente (ex.: falso positivo):

```bash
sudo fail2ban-client set estilomarcado-scan unbanip <IP>
```

---

## 6. Firewall

Exponha apenas o necessario:

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status verbose
```

---

## 7. Verificacao

```bash
# Redirecionamento HTTP -> HTTPS.
curl -sI http://estilomarcado.samuelsantos.qzz.io | head -n1

# Cabecalhos de seguranca.
curl -sI https://estilomarcado.samuelsantos.qzz.io | grep -iE 'strict-transport|x-content-type|x-frame|content-security'

# Bloqueio de scanner (deve encerrar sem resposta / status 444).
curl -s -o /dev/null -w '%{http_code}\n' https://estilomarcado.samuelsantos.qzz.io/.env

# Portas internas NAO respondem pela Internet.
curl -s --max-time 5 http://<IP-DO-SERVIDOR>:8080 || echo "backend inacessivel (correto)"
curl -s --max-time 5 http://<IP-DO-SERVIDOR>:5432 || echo "postgres inacessivel (correto)"
```

Teste do jail de login (cuidado: bane o IP de teste):

```bash
for i in $(seq 1 6); do
  curl -s -o /dev/null -w '%{http_code}\n' \
    -X POST https://estilomarcado.samuelsantos.qzz.io/api/autenticacao/sessoes \
    -H 'Content-Type: application/json' \
    -d '{"email":"nao-existe@exemplo.com","senha":"errada123"}'
done
sudo fail2ban-client status estilomarcado-auth
```

---

## 8. Atualizacao (novas versoes)

```bash
cd /opt/estilo-marcado
git pull --ff-only

# Reaplica as configuracoes do nginx do host. Alteracoes de vhost (headers,
# CSP, rate limit e proxy) NAO entram no `docker compose up`, que so reconstroi
# os containers.
sudo cp deploy/nginx/conf.d/estilomarcado-limits.conf \
        /etc/nginx/conf.d/estilomarcado-limits.conf
sudo cp deploy/nginx/proxy_params_estilomarcado.conf \
        /etc/nginx/proxy_params_estilomarcado.conf
sudo cp deploy/nginx/sites-available/estilomarcado.samuelsantos.qzz.io.conf \
        /etc/nginx/sites-available/
sudo nginx -t && sudo systemctl reload nginx

docker compose --env-file deploy/.env.production -f docker-compose.prod.yml up -d --build
```

Limpeza de imagens antigas (opcional):

```bash
docker image prune -f
```

---

## 9. Backup e operacao

```bash
# Backup do banco.
docker compose --env-file deploy/.env.production -f docker-compose.prod.yml \
  exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > backup-$(date +%F).sql

# Logs.
docker compose --env-file deploy/.env.production -f docker-compose.prod.yml logs -f --tail=100

# Parar sem apagar dados (volume nomeado permanece).
docker compose --env-file deploy/.env.production -f docker-compose.prod.yml down
```

---

## Solucao de problemas

- **Tela sem estilos / "raw html" e, no console, `Executing inline event
  handler violates ... script-src 'self'`:** o build de producao do Angular, com
  `inlineCritical` (padrao), injeta um `<link ... onload="this.media='all'">` no
  `index.html`. A CSP com `script-src 'self'` bloqueia esse handler e a folha de
  estilos nunca e aplicada. Por isso `src/frontend/angular.json` desativa
  `inlineCritical` no perfil de producao. Ao atualizar o frontend, confirme essa
  opcao e reconstrua a imagem.
- **Fontes nao carregam:** a CSP precisa manter `https://fonts.gstatic.com` em
  `font-src` (arquivos `.woff2`) e `https://fonts.googleapis.com` em `style-src`.
- **"Continuar com Google" falha e o console informa bloqueio de
  `https://apis.google.com/js/api.js`:** atualize o vhost nginx do host com a
  versao deste repositorio. O Firebase Auth precisa carregar esse script,
  acessar as APIs de identidade e abrir o iframe de
  `estilomarcado.firebaseapp.com`. Depois de copiar o vhost, execute
  `sudo nginx -t && sudo systemctl reload nginx` e confira o header CSP em
  `curl -sI https://estilomarcado.samuelsantos.qzz.io/entrar`.
- **A foto da conta Google (`lh3.googleusercontent.com`) nao carrega e o console
  mostra `violates ... "img-src 'self' data: blob:"`:** o vhost do host ainda
  esta com a CSP antiga, sem `https://lh3.googleusercontent.com`. O `img-src`
  correto esta neste repositorio (o campo `fotoPerfilUrl` so aponta para esse
  host). Recopie o vhost, rode `sudo nginx -t && sudo systemctl reload nginx` e
  confirme com `curl -sI https://estilomarcado.samuelsantos.qzz.io/ | grep -i
  content-security`. Reconstruir apenas o frontend **nao** altera esse header.
- **Headers duplicados (`X-Frame-Options`, `X-Content-Type-Options`):** normais;
  o Spring Security adiciona os mesmos headers nas respostas da API.

## Resumo dos arquivos

| Arquivo | Destino no servidor |
| --- | --- |
| `docker-compose.prod.yml` | raiz do projeto |
| `deploy/.env.production` | raiz do projeto (segredo, nao versionado) |
| `deploy/nginx/conf.d/estilomarcado-limits.conf` | `/etc/nginx/conf.d/` |
| `deploy/nginx/proxy_params_estilomarcado.conf` | `/etc/nginx/` |
| `deploy/nginx/sites-available/*.conf` | `/etc/nginx/sites-available/` |
| `deploy/fail2ban/filter.d/*.conf` | `/etc/fail2ban/filter.d/` |
| `deploy/fail2ban/jail.d/estilomarcado.local` | `/etc/fail2ban/jail.d/` |
