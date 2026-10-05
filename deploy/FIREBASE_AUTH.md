# Configuração do Firebase Authentication

A aplicação continua guardando perfis, permissões, agendamentos e a sessão HTTP
no PostgreSQL. O Firebase guarda as credenciais de e-mail/senha e Google.
`FIREBASE_AUTH_ENABLED=false` mantém o fluxo anterior apenas durante a
preparação. Ao habilitar Firebase, o endpoint de senha local deixa de aceitar
login; não há fallback automático.

## 1. Preparar o projeto Firebase

1. Crie ou escolha um projeto no [Firebase Console](https://console.firebase.google.com/).
2. Em **Authentication > Sign-in method**, habilite **Email/Password** e
   **Google**. Configure o e-mail de suporte solicitado pelo Google.
3. Em **Authentication > Settings > Authorized domains**, adicione o domínio
   público da aplicação e `localhost` para desenvolvimento.
4. Em **Project settings > Your apps**, registre um aplicativo Web. Copie
   `apiKey`, `authDomain`, `projectId` e `appId` da configuração exibida.
5. Em **Authentication > Templates**, edite uma mensagem de e-mail e use
   **Customize action URL** com `https://SEU-DOMINIO/acao-email`. O Firebase
   aplica esse URL a todos os modelos. A rota encaminha verificação de e-mail
   e redefinição de senha às telas corretas. Para testar localmente com projeto
   real, use temporariamente `http://localhost:4200/acao-email` nesse campo.
   No domínio de produção do projeto, o valor é
   `https://estilomarcado.samuelsantos.qzz.io/acao-email`.
6. Em **Project settings > Service accounts**, gere uma chave privada da conta
   de serviço. Guarde o JSON em `deploy/firebase/service-account.json` no host.
   Essa pasta é ignorada pelo Git. Restrinja a leitura do arquivo à conta que
   executa o Docker. Não cole a chave em mensagens nem a inclua no frontend.

## 2. Preencher ambiente

Copie `.env.example` para `.env` em desenvolvimento ou
`deploy/env.production.example` para `deploy/.env.production` em produção.

| Variável | Valor esperado |
| --- | --- |
| `FIREBASE_PROJECT_ID` | `projectId` do app Web. |
| `FIREBASE_WEB_API_KEY` | `apiKey` do app Web; é configuração pública, não a chave privada Admin. |
| `FIREBASE_WEB_AUTH_DOMAIN` | `authDomain` do app Web, geralmente `<projeto>.firebaseapp.com`. |
| `FIREBASE_WEB_APP_ID` | `appId` do app Web. |
| `FIREBASE_ADMIN_CREDENTIALS_PATH` | `/run/firebase/service-account.json` nos contêineres Docker. |
| `FIREBASE_AUTH_ENABLED` | `false` até concluir a preparação; `true` no corte. |
| `FIREBASE_AUTH_IMPORT_EXISTING` | `true` apenas na primeira execução da importação; depois `false`. |
| `FRONTEND_URL` | Origem exata da aplicação, com `https://` em produção. |
| `AUTH_ALLOWED_ORIGINS` | Origens web autorizadas pelo backend, separadas por vírgula. |
| `SESSION_COOKIE_SECURE` | `true` em produção HTTPS. |

O Docker Compose monta `deploy/firebase/` como `/run/firebase/` somente no
backend. O frontend recebe do backend apenas a configuração pública do SDK.
SMTP continua necessário para convites, verificação, recuperação e avisos;
Mailpit atende ao desenvolvimento, e produção exige SMTP real. Defina
`MAIL_FROM` com um remetente aceito pelo provedor SMTP do seu domínio.

## 3. Importar contas existentes e fazer o corte

1. Faça backup do PostgreSQL e confirme que os e-mails locais são únicos e
   válidos. Não remova os hashes BCrypt antes da conferência.
2. Preencha as variáveis e o JSON Admin. Defina
   `FIREBASE_AUTH_ENABLED=true` e `FIREBASE_AUTH_IMPORT_EXISTING=true`.
3. Recrie o backend com o arquivo de ambiente apropriado. A migração Flyway
   cria `usuario.firebase_uid`; o importador usa UID `usuario-<id>`, confere
   colisões remotas antes de cada conta, importa BCrypt quando houver e
   invalida as sessões locais ao concluir. Uma falha interrompe o arranque;
   corrija a colisão e execute novamente. O processo é retomável.
4. Confira a mensagem `Importacao Firebase concluida`, a quantidade de contas
   no console e o login de uma amostra de cliente, profissional, recepção e
   administrador. Verifique que os agendamentos e permissões não mudaram.
5. Defina `FIREBASE_AUTH_IMPORT_EXISTING=false` e recrie o backend. Mantenha
   `FIREBASE_AUTH_ENABLED=true`. A aplicação não inicia se houver conta local
   sem UID após o corte.

O hash local permanece no banco para uma janela de reversão e backup; o login
Firebase habilitado não o consulta. A remoção definitiva dos hashes deve ser
feita em migração posterior, depois da conferência operacional. Uma mudança de
e-mail ou desativação feita diretamente no Firebase deve ser reconciliada com
o banco da aplicação; o job diário registra divergências, repete a invalidação
de sessões com tokens revogados e não une contas por e-mail automaticamente.

## Desenvolvimento com Auth Emulator

Use `FIREBASE_PROJECT_ID=demo-estilo-marcado`, valores de configuração Web do
mesmo projeto de teste e:

```text
FIREBASE_AUTH_EMULATOR_HOST=host.docker.internal:9099
FIREBASE_AUTH_EMULATOR_BROWSER_URL=http://localhost:9099
```

O Admin SDK usa o emulador quando `FIREBASE_AUTH_EMULATOR_HOST` está definido;
nesse caso, não exige JSON de conta de serviço. A URL do navegador é separada
porque o contêiner e o navegador alcançam o emulador por endereços diferentes.
Nunca configure essas variáveis em produção. O emulador não substitui a
validação final dos links de e-mail e do Google OAuth em um projeto real.

## Referências

- [Configurar provedores](https://firebase.google.com/docs/auth/web/start)
- [Importar usuários BCrypt](https://firebase.google.com/docs/auth/admin/import-users)
- [Ações de e-mail personalizadas](https://firebase.google.com/docs/auth/custom-email-handler)
- [Conectar Auth Emulator](https://firebase.google.com/docs/emulator-suite/connect_auth)
