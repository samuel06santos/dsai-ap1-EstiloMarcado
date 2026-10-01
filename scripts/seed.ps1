#Requires -Version 5.1
<#
.SYNOPSIS
    Semeia o banco de desenvolvimento do Estilo Marcado com dados mock.

.DESCRIPTION
    Executa scripts/seed.sql no container PostgreSQL do docker compose.
    Rode este script DEPOIS de subir os containers:

        docker compose up --build -d
        scripts/seed.ps1

    A seed e idempotente: rodar varias vezes nao duplica registros.

.PARAMETER Reset
    Limpa os dados de dominio antes de semear (nao apaga as migracoes do Flyway).

.PARAMETER Container
    Nome do container PostgreSQL. Padrao: estilo-marcado-postgres.

.EXAMPLE
    scripts/seed.ps1

.EXAMPLE
    scripts/seed.ps1 -Reset
#>
[CmdletBinding()]
param(
    [switch]$Reset,
    [string]$Container = $(if ($env:SEED_CONTAINER) { $env:SEED_CONTAINER } else { 'estilo-marcado-postgres' })
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
$seedFile = Join-Path $scriptDir 'seed.sql'
$remoteSeedFile = '/tmp/estilo-marcado-seed.sql'

function Fail([string]$message) {
    Write-Host "ERRO: $message" -ForegroundColor Red
    exit 1
}

function Get-ContainerEnv([string]$name) {
    $value = (& docker exec $Container printenv $name 2>$null)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($value)) {
        Fail "nao foi possivel ler $name do container '$Container'."
    }
    return $value.Trim()
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Fail 'docker nao encontrado no PATH. Instale/inicie o Docker Desktop.'
}

if (-not (Test-Path -LiteralPath $seedFile)) {
    Fail "arquivo de seed nao encontrado: $seedFile"
}

Write-Host 'Verificando o container PostgreSQL...' -ForegroundColor Cyan
$running = (& docker inspect -f '{{.State.Running}}' $Container 2>$null)
if ($LASTEXITCODE -ne 0 -or $running -ne 'true') {
    Fail "o container '$Container' nao esta em execucao. Rode 'docker compose up --build -d' antes de semear."
}

$dbUser = Get-ContainerEnv 'POSTGRES_USER'
$dbName = Get-ContainerEnv 'POSTGRES_DB'

if ($Reset) {
    Write-Host 'Limpando dados de dominio (reset)...' -ForegroundColor Yellow
    $truncate = @'
TRUNCATE TABLE
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
'@
    & docker exec -i $Container psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName -c $truncate
    if ($LASTEXITCODE -ne 0) { Fail 'falha ao limpar os dados de dominio.' }
}

Write-Host 'Copiando scripts/seed.sql para o container...' -ForegroundColor Cyan
& docker cp $seedFile "${Container}:$remoteSeedFile"
if ($LASTEXITCODE -ne 0) { Fail 'falha ao copiar o arquivo de seed para o container.' }

try {
    Write-Host 'Semeando o banco de dados...' -ForegroundColor Cyan
    & docker exec -i $Container psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName -f $remoteSeedFile
    if ($LASTEXITCODE -ne 0) { Fail 'falha ao executar a seed.' }
}
finally {
    & docker exec -i $Container rm -f $remoteSeedFile | Out-Null
}

Write-Host ''
Write-Host 'Banco semeado com sucesso!' -ForegroundColor Green
Write-Host ''
Write-Host 'Contas mock (senha: Estilo@2026):' -ForegroundColor Cyan
Write-Host '  admin@estilomarcado.dev          -> ADMINISTRADOR (Unidade Centro)'
Write-Host '  recepcao@estilomarcado.dev       -> RECEPCAO      (Unidade Centro)'
Write-Host '  admin.batista@estilomarcado.dev  -> ADMINISTRADOR (Unidade Batista Campos)'
Write-Host '  ana.souza@estilomarcado.dev      -> PROFISSIONAL  (Ana Souza)'
Write-Host '  carlos.lima@estilomarcado.dev    -> PROFISSIONAL  (Carlos Lima)'
Write-Host '  beatriz.rocha@estilomarcado.dev  -> PROFISSIONAL  (Beatriz Rocha)'
Write-Host '  diego.mendes@estilomarcado.dev   -> PROFISSIONAL  (Diego Mendes)'
Write-Host '  cliente@estilomarcado.dev        -> CLIENTE'
Write-Host '  joao.pereira@estilomarcado.dev   -> CLIENTE'
Write-Host '  maria.oliveira@estilomarcado.dev -> CLIENTE'
