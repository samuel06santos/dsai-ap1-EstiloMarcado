# Ambiente de desenvolvimento local (2026-09-30)

## O que e e por que existe

O ambiente de desenvolvimento local permite que a equipe execute o Estilo
Marcado de maneira previsivel, sem instalar manualmente o banco de dados ou
servicos auxiliares em cada computador. Ele tambem aproxima o ambiente local da
forma como a aplicacao sera publicada.

## Componentes

- **PostgreSQL:** banco de dados relacional da aplicacao.
- **Backend:** API Spring Boot que se conecta ao banco e expoe uma verificacao
  de saude.
- **Frontend:** aplicacao Angular acessivel no navegador.
- **pgAdmin:** interface local opcional para inspecao do banco de dados.
- **Mailpit:** caixa de entrada local para visualizar e testar emails sem enviar
  mensagens a destinatarios reais.

## Criterios de aceitacao

- Um arquivo `.env.example` documenta todas as variaveis necessarias e pode ser
  copiado para `.env` sem conter segredos reais no repositorio.
- `docker compose up --build` inicia banco, backend, frontend, pgAdmin e
  Mailpit a partir de um clone limpo, desde que Docker esteja instalado.
- O backend aguarda o banco ficar saudavel antes de iniciar.
- O backend responde ao endpoint de saude do Spring Boot.
- O frontend fica acessivel na porta documentada no README.
- Dados do PostgreSQL persistem em um volume nomeado do Docker, sem serem
  versionados pelo Git.
- Arquivos de configuracao locais e segredos permanecem ignorados pelo Git.

## Fora do escopo

- Implantacao em producao, certificados TLS e configuracao do dominio.
- Integracao com provedores reais de email ou mensageria.
- Banco de dados com schema funcional completo; este sera definido nas specs de
  cada modulo de dominio.
- Observabilidade e monitoramento de producao.

## Decisoes

- O Docker Compose e a fonte de verdade para iniciar os servicos locais.
- As imagens de desenvolvimento priorizam recarga rapida e clareza, nao tamanho
  minimo de imagem de producao.
- As credenciais presentes em `.env.example` servem apenas para desenvolvimento
  local; valores de producao nao devem ser versionados.
- O frontend chama o backend pela URL interna da rede Docker quando necessario,
  enquanto o navegador acessa cada servico pelas portas locais documentadas.

## Relacao com outras specs

Esta spec descreve somente a infraestrutura de desenvolvimento. A autenticacao,
o schema de negocio, os endpoints e as telas serao definidos em specs proprias
antes de sua implementacao.
