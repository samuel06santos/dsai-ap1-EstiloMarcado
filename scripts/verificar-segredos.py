#!/usr/bin/env python3
"""Verifica (e opcionalmente expurga) segredos em arquivos de texto.

Uso:
    python scripts/verificar-segredos.py [caminhos...]
    python scripts/verificar-segredos.py --incluir-senhas [caminhos...]
    python scripts/verificar-segredos.py --redigir [--incluir-senhas] [caminhos...]

Sem caminhos, varre `prompts/sessoes/`. Cada caminho pode ser um arquivo ou
diretorio. Em modo de verificacao o codigo de saida e 1 quando ha achados, o que
permite usar o script em hooks de pre-commit; em modo `--redigir` os valores
sensiveis sao substituidos por `<REDACTED:padrao>` e a saida passa a ser 0.

Padroes de alta confianca rodam sempre (chaves de API, tokens, chaves privadas,
URLs com credencial, cookies de sessao). O heurístico de senha concreta
(`--incluir-senhas`) tem maior cobertura e mais falsos positivos; use-o nas
exportacoes de sessao. O script apenas reporta e substitui, nunca usa a rede.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# Alta confianca: formato inequivoco de credencial.
PADROES_ALTA = {
    "google_api_key": re.compile(r"(?P<segredo>AIza[0-9A-Za-z_\-]{35,})"),
    "google_oauth_client_secret": re.compile(r"(?P<segredo>GOCSPX-[A-Za-z0-9_\-]{20,})"),
    "aws_access_key_id": re.compile(r"(?P<segredo>AKIA[0-9A-Z]{16})"),
    "github_token": re.compile(r"(?P<segredo>gh[pousr]_[A-Za-z0-9]{36,})"),
    "github_pat": re.compile(r"(?P<segredo>github_pat_[A-Za-z0-9_]{22,})"),
    "slack_token": re.compile(r"(?P<segredo>xox[baprs]-[A-Za-z0-9-]{10,})"),
    "stripe_secret": re.compile(r"(?P<segredo>sk_(?:live|test)_[A-Za-z0-9]{16,})"),
    "twilio_key": re.compile(r"(?P<segredo>SK[0-9a-fA-F]{32})"),
    "chave_privada": re.compile(r"(?P<segredo>-----BEGIN [A-Z ]*PRIVATE KEY-----)"),
    "url_com_credencial": re.compile(
        r"(?P<segredo>[a-zA-Z][a-zA-Z0-9+.\-]*://[^\s/:@\"']+:[^\s/@\"']+@[A-Za-z0-9.\-]+[^\s\"']*)"
    ),
    "bearer_token": re.compile(r"(?P<segredo>(?i:bearer)\s+[A-Za-z0-9._\-]{20,})"),
    "jwt": re.compile(
        r"(?P<segredo>eyJ[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{6,})"
    ),
    "session_cookie": re.compile(r"(?P<segredo>(?:SESSION|JSESSIONID)=[A-Za-z0-9._\-]{16,})"),
    "xsrf_cookie": re.compile(r"(?P<segredo>XSRF-TOKEN=[A-Za-z0-9._\-]{16,})"),
}

# Heurística: `chave=valor` para nomes usuais de segredo. Roda com --incluir-senhas.
PADROES_SENHA = {
    "senha_concreta": re.compile(
        r"(?i)(?<![A-Za-z0-9])"
        r"(?:client_secret|refresh_token|access_token|password|passwd|senha|pwd|secret|api[_-]?key)"
        r"(?![A-Za-z0-9_])\s*[=:]\s*[\"']?"
        r"(?P<segredo>[A-Za-z0-9@!#%._+\-]{8,})"
    ),
}

# Valores sabidamente nao sigilosos (seed, exemplos e placeholders).
PERMITIDOS = {
    "estilo@2026",
    "estilo-marcado-dev-2026",
    "troque-esta-senha-local",
    "senha-ficticia-2026",
    "password",
    "senha",
    "secret",
    "token",
    "apikey",
    "api-key",
    "true",
    "false",
    "null",
    "undefined",
    "changeme",
    "example",
    "exemplo",
    "sua-senha",
    "placeholder",
}

MARCADORES_INOCUOS = ("exemplo", "example", "fake", "dummy", "sample", "placeholder", "changeme", "redacted", "troque")

uuid_re = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
hash_re = re.compile(r"^[0-9a-fA-F]{32}$|^[0-9a-fA-F]{40}$|^[0-9a-fA-F]{64}$")

IGNORAR_EXTENSOES = {".png", ".jpg", ".jpeg", ".gif", ".pdf", ".zip", ".gz", ".woff", ".woff2"}


def mascarar(valor: str) -> str:
    return "***" if len(valor) <= 8 else f"{valor[:4]}...{valor[-4:]} (len={len(valor)})"


def permitido(valor: str) -> bool:
    limpo = valor.strip().strip("\"'`,;:)")
    if not limpo:
        return True
    baixo = limpo.lower()
    if "://localhost" in baixo or "://127.0.0.1" in baixo:
        return True
    if baixo in PERMITIDOS or any(m in baixo for m in MARCADORES_INOCUOS):
        return True
    if limpo.startswith(("${", "<", "*")):
        return True
    if "REDACTED" in limpo.upper() or "***" in limpo:
        return True
    if uuid_re.match(limpo) or hash_re.match(limpo):
        return True
    return False


def padroes(incluir_senhas: bool) -> dict[str, re.Pattern[str]]:
    ativos = dict(PADROES_ALTA)
    if incluir_senhas:
        ativos.update(PADROES_SENHA)
    return ativos


def arquivos_de(caminhos: list[str], padrao: str) -> list[Path]:
    if not caminhos:
        caminhos = [padrao]
    encontrados: list[Path] = []
    for bruto in caminhos:
        caminho = Path(bruto)
        if caminho.is_dir():
            encontrados.extend(
                p for p in sorted(caminho.rglob("*"))
                if p.is_file() and p.suffix.lower() not in IGNORAR_EXTENSOES
            )
        elif caminho.is_file():
            encontrados.append(caminho)
        else:
            print(f"aviso: caminho ignorado (nao encontrado): {bruto}", file=sys.stderr)
    return encontrados


def varrer(texto: str, ativos: dict[str, re.Pattern[str]]) -> list[tuple[str, int, str]]:
    """Retorna (padrao, linha, valor), sem repetir o mesmo valor na mesma linha."""
    achados: list[tuple[str, int, str]] = []
    vistos: set[tuple[int, str]] = set()
    for nome, regex in ativos.items():
        for m in regex.finditer(texto):
            valor = m.group("segredo")
            if permitido(valor):
                continue
            linha = texto.count("\n", 0, m.start("segredo")) + 1
            if (linha, valor) in vistos:
                continue
            vistos.add((linha, valor))
            achados.append((nome, linha, valor))
    return achados


def redigir(texto: str, ativos: dict[str, re.Pattern[str]]) -> tuple[str, int]:
    total = 0

    def substituir(nome: str, m: re.Match[str]) -> str:
        nonlocal total
        if permitido(m.group("segredo")):
            return m.group(0)
        total += 1
        bruto = m.group(0)
        inicio, fim = m.span("segredo")
        return bruto[: inicio - m.start()] + f"<REDACTED:{nome}>" + bruto[fim - m.start():]

    resultado = texto
    for nome, regex in ativos.items():
        resultado = regex.sub(lambda m, nome=nome: substituir(nome, m), resultado)
    return resultado, total


def main() -> int:
    parser = argparse.ArgumentParser(description="Verifica/expurga segredos em arquivos de texto.")
    parser.add_argument("caminhos", nargs="*", help="arquivos ou diretorios (padrao: prompts/sessoes)")
    parser.add_argument("--redigir", action="store_true", help="substitui os segredos por <REDACTED:padrao>")
    parser.add_argument("--incluir-senhas", action="store_true", help="inclui a heuristica de senha concreta")
    parser.add_argument("--padrao", default="prompts/sessoes", help="diretorio padrao (padrao: prompts/sessoes)")
    args = parser.parse_args()

    ativos = padroes(args.incluir_senhas)
    arquivos = arquivos_de(args.caminhos, args.padrao)
    if not arquivos:
        print("nenhum arquivo para verificar")
        return 0

    if args.redigir:
        total = 0
        for caminho in arquivos:
            with open(caminho, "r", encoding="utf-8", errors="replace", newline="") as f:
                original = f.read()
            resultado, n = redigir(original, ativos)
            if n:
                with open(caminho, "w", encoding="utf-8", newline="") as f:
                    f.write(resultado)
                print(f"redigido: {caminho} ({n} segredo(s))")
                total += n
        print(f"total redigido: {total}")
        return 0

    total = 0
    for caminho in arquivos:
        texto = caminho.read_text(encoding="utf-8", errors="replace")
        for nome, linha, valor in varrer(texto, ativos):
            total += 1
            print(f"{caminho}:{linha}: [{nome}] {mascarar(valor)}")
    if total:
        print(f"\n{total} possivel(is) segredo(s) encontrado(s). Rode com --redigir para expurgar.")
        return 1
    print(f"nenhum segredo encontrado em {len(arquivos)} arquivo(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
