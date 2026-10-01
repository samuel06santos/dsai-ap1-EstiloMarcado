#!/usr/bin/env bash
#
# Semeia o banco de desenvolvimento do Estilo Marcado com dados mock.
#
# Rode DEPOIS de subir os containers:
#
#     docker compose up --build -d
#     scripts/seed.sh
#
# Opcoes:
#     --reset                Limpa os dados de dominio antes de semear.
#     -c, --container NOME   Nome do container PostgreSQL.
#                            Padrao: estilo-marcado-postgres
#     -h, --help             Mostra esta ajuda.
#
# A seed e idempotente: rodar varias vezes nao duplica registros.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SEED_FILE="${SCRIPT_DIR}/seed.sql"
REMOTE_SEED_FILE="/tmp/estilo-marcado-seed.sql"
CONTAINER="${SEED_CONTAINER:-estilo-marcado-postgres}"
RESET="false"

while [ $# -gt 0 ]; do
    case "$1" in
        --reset) RESET="true"; shift ;;
        -c|--container) CONTAINER="$2"; shift 2 ;;
        -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "Opcao desconhecida: $1" >&2; exit 1 ;;
    esac
done

fail() {
    echo "ERRO: $1" >&2
    exit 1
}

command -v docker >/dev/null 2>&1 || fail "docker nao encontrado no PATH."
[ -f "$SEED_FILE" ] || fail "arquivo de seed nao encontrado: $SEED_FILE"

echo "Verificando o container PostgreSQL..."
if [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null || echo false)" != "true" ]; then
    fail "o container '$CONTAINER' nao esta em execucao. Rode 'docker compose up --build -d' antes de semear."
fi

DB_USER="$(docker exec "$CONTAINER" printenv POSTGRES_USER)"
DB_NAME="$(docker exec "$CONTAINER" printenv POSTGRES_DB)"
[ -n "$DB_USER" ] || fail "nao foi possivel ler POSTGRES_USER do container."
[ -n "$DB_NAME" ] || fail "nao foi possivel ler POSTGRES_DB do container."

if [ "$RESET" = "true" ]; then
    echo "Limpando dados de dominio (reset)..."
    docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" <<'SQL'
TRUNCATE TABLE
    notificacao_outbox,
    notificacao_interna,
    notificacao_preferencia,
    lista_espera_evento,
    lista_espera_oferta,
    lista_espera,
    agendamento_evento,
    agendamento_idempotencia,
    bloqueio_agenda,
    evento_seguranca,
    token_usuario,
    atendimento,
    usuario,
    excecao_jornada_intervalo,
    excecao_jornada,
    afastamento,
    feriado,
    jornada_intervalo,
    servico_profissional,
    servico,
    profissional,
    cliente,
    unidade,
    estabelecimento
RESTART IDENTITY CASCADE;
SQL
fi

echo "Copiando scripts/seed.sql para o container..."
docker cp "$SEED_FILE" "${CONTAINER}:${REMOTE_SEED_FILE}"

cleanup() {
    docker exec -i "$CONTAINER" rm -f "$REMOTE_SEED_FILE" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Semeando o banco de dados..."
docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" -f "$REMOTE_SEED_FILE"

echo
echo "Banco semeado com sucesso!"
echo
echo "Contas mock (senha: Estilo@2026):"
echo "  admin@estilomarcado.dev          -> ADMINISTRADOR (Unidade Centro)"
echo "  recepcao@estilomarcado.dev       -> RECEPCAO      (Unidade Centro)"
echo "  admin.batista@estilomarcado.dev  -> ADMINISTRADOR (Unidade Batista Campos)"
echo "  ana.souza@estilomarcado.dev      -> PROFISSIONAL  (Ana Souza)"
echo "  carlos.lima@estilomarcado.dev    -> PROFISSIONAL  (Carlos Lima)"
echo "  beatriz.rocha@estilomarcado.dev  -> PROFISSIONAL  (Beatriz Rocha)"
echo "  diego.mendes@estilomarcado.dev   -> PROFISSIONAL  (Diego Mendes)"
echo "  cliente@estilomarcado.dev        -> CLIENTE"
echo "  joao.pereira@estilomarcado.dev   -> CLIENTE"
echo "  maria.oliveira@estilomarcado.dev -> CLIENTE"
