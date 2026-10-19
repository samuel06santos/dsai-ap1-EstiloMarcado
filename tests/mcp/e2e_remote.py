"""Exercita o transporte MCP e OAuth reais contra o Compose isolado de teste.

Execute dentro da rede Compose com FastMCP instalado. Não imprime tokens nem senhas.
"""

import asyncio
import base64
import hashlib
import os
import re
import secrets
from urllib.parse import parse_qs, urlparse

import httpx
import httpx2
from fastmcp import Client
from fastmcp.client.elicitation import ElicitResult


BACKEND = os.environ.get("MCP_TEST_BACKEND", "http://backend:8080")
MCP = os.environ.get("MCP_TEST_URL", "http://mcp:8000/mcp")
MAILPIT = os.environ.get("MCP_TEST_MAILPIT", "http://mailpit:8025")
REDIRECT = "http://127.0.0.1:17891/callback"


def exigir(response, status):
    if response.status_code != status:
        raise AssertionError(f"{response.request.method} {response.request.url.path}: "
                             f"HTTP {response.status_code}, esperado {status}: {response.text[:300]}")
    return response


def conta_e_token():
    email = f"mcp-e2e-{secrets.token_hex(6)}@example.test"
    senha = "Senha" + secrets.token_hex(12) + "9"
    browser = httpx.Client(base_url=BACKEND, timeout=15, follow_redirects=False)
    try:
        exigir(browser.get("/api/autenticacao/csrf"), 200)
        csrf = browser.cookies["XSRF-TOKEN"]
        cabecalho = {"X-XSRF-TOKEN": csrf}
        exigir(browser.post("/api/autenticacao/cadastros", headers=cabecalho, json={
            "nome": "Cliente MCP E2E", "email": email, "senha": senha,
            "confirmacaoSenha": senha}), 202)
        caixa = exigir(httpx.get(MAILPIT + "/api/v1/messages", timeout=15), 200).json()
        mensagem = next(m for m in caixa["messages"] if email in str(m.get("To", [])))
        detalhe = exigir(httpx.get(MAILPIT + "/api/v1/message/" + mensagem["ID"], timeout=15), 200).json()
        token_ativacao = re.search(r"[?&]token=([A-Za-z0-9_-]+)", detalhe["Text"])
        assert token_ativacao, "e-mail sem token de ativação"
        exigir(browser.post("/api/autenticacao/ativacoes", headers=cabecalho,
                            json={"token": token_ativacao.group(1)}), 204)
        login = exigir(browser.post("/api/autenticacao/sessoes", headers=cabecalho,
                                    json={"email": email, "senha": senha}), 200)
        assert login.json()["perfil"] == "CLIENTE"
        assert "SESSION" in browser.cookies, "login não criou sessão web"
        registro = exigir(browser.post("/oauth/register", json={
            "client_name": "Teste MCP isolado", "redirect_uris": [REDIRECT]}), 201).json()
        client_id = registro["client_id"]
        verifier = secrets.token_urlsafe(48)
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
        state = secrets.token_urlsafe(20)
        pedido = {"response_type": "code", "client_id": client_id, "redirect_uri": REDIRECT,
                  "code_challenge": challenge, "code_challenge_method": "S256",
                  "scope": "mcp:read mcp:write", "state": state,
                  "resource": os.environ.get("MCP_TEST_AUDIENCE", "http://localhost:18000/mcp")}
        pagina = exigir(browser.get("/oauth/authorize", params=pedido), 200)
        assert "Autorizar aplicativo" in pagina.text
        assert "/oauth/consent.css" in pagina.text
        assert "form-action 'self' http://127.0.0.1:17891" in pagina.headers["content-security-policy"]
        form_csrf = re.search(r'name="_csrf" value="([^"]+)"', pagina.text)
        assert form_csrf, "formulário de consentimento sem CSRF"
        consentimento = exigir(browser.post("/oauth/authorize", data={**pedido,
                                  "decisao": "permitir", "_csrf": form_csrf.group(1)}), 302)
        retorno = urlparse(consentimento.headers["location"])
        assert retorno.scheme == "http" and retorno.netloc == "127.0.0.1:17891"
        query = parse_qs(retorno.query)
        assert query["state"] == [state], "state divergente"
        codigo = query["code"][0]
        resposta = exigir(httpx.post(BACKEND + "/oauth/token", data={
            "grant_type": "authorization_code", "code": codigo, "client_id": client_id,
            "redirect_uri": REDIRECT, "code_verifier": verifier}, timeout=15), 200).json()
        jwt = resposta["access_token"]
        refresh = resposta["refresh_token"]
        assert resposta["token_type"] == "Bearer" and resposta["expires_in"] <= 300
        assert len(refresh) == 43
        print("Conta local, SESSION, consentimento PKCE e troca do código: OK")
        return jwt, refresh, browser, client_id
    except Exception:
        browser.close()
        raise


async def cliente_mcp(jwt):
    vistos = []
    enviar = httpx2.AsyncClient.send

    async def capturar(self, request, *args, **kwargs):
        response = await enviar(self, request, *args, **kwargs)
        if request.url.path == "/mcp":
            vistos.append((request.headers.get("Authorization"), response.status_code))
        return response

    httpx2.AsyncClient.send = capturar
    try:
        async with Client(MCP) as anonimo:
            tools = await anonimo.list_tools()
            assert len(tools) == 10, [tool.name for tool in tools]
            publico = await anonimo.call_tool("listar_filiais", {})
            assert not publico.is_error, publico
            try:
                privado = await anonimo.call_tool("minha_lista_espera", {})
                assert privado.is_error or "AUTENTICACAO_MCP_NECESSARIA" in str(privado)
            except Exception:
                assert (None, 401) in vistos, ("desafio anônimo não retornou HTTP 401; "
                                              + str([(bool(value), status) for value, status in vistos]))
        print("Cliente MCP real: descoberta, leitura pública e desafio anônimo: OK")
        async with Client(MCP, auth=jwt) as autorizado:
            privado = await autorizado.call_tool("minha_lista_espera", {})
            assert not privado.is_error, privado
        assert any(value and value.startswith("Bearer ") and status == 200
                   for value, status in vistos), "cliente não enviou Bearer na chamada MCP"
        print("Cliente MCP real: Authorization Bearer capturado e leitura privada: OK")
        recusas = []

        async def recusar(mensagem, tipo, parametros, contexto):
            recusas.append(mensagem)
            return ElicitResult(action="decline")

        async with Client(MCP, auth=jwt, elicitation_handler=recusar) as cliente:
            try:
                resultado = await cliente.call_tool("criar_agendamento", {
                    "filial_id": 999999, "servico_id": 999999, "inicio": "2030-01-07T09:00:00"})
                assert resultado.is_error, resultado
            except Exception:
                assert recusas, "a escrita falhou antes de pedir confirmação"
        assert recusas and "Criar agendamento" in recusas[0]
        print("Cliente MCP real: recusa explícita de escrita antes do backend: OK")
    finally:
        httpx2.AsyncClient.send = enviar


async def token_revogado(jwt):
    async with Client(MCP, auth=jwt) as cliente:
        try:
            resultado = await cliente.call_tool("minha_lista_espera", {})
        except Exception:
            pass
        else:
            assert resultado.is_error, "token revogado expôs dados privados"
    print("Cliente MCP real: Bearer revogado recusado: OK")


def main():
    jwt, refresh, browser, client_id = conta_e_token()
    try:
        asyncio.run(cliente_mcp(jwt))
        rejeicao = exigir(httpx.post(MCP, headers={"Authorization": "Bearer expirado"}, json={
            "jsonrpc": "2.0", "id": 1, "method": "tools/list"}, timeout=15), 401)
        assert 'error="invalid_token"' in rejeicao.headers["WWW-Authenticate"]
        renovado = exigir(httpx.post(BACKEND + "/oauth/token", data={
            "grant_type": "refresh_token", "refresh_token": refresh,
            "client_id": client_id,
            "resource": os.environ.get("MCP_TEST_AUDIENCE", "http://localhost:18000/mcp")}, timeout=15), 200).json()
        assert renovado["refresh_token"] != refresh
        assert exigir(httpx.post(BACKEND + "/oauth/token", data={
            "grant_type": "refresh_token", "refresh_token": refresh,
            "client_id": client_id}, timeout=15), 400).json()["error"] == "invalid_grant"
        async def ler_com_token_renovado():
            async with Client(MCP, auth=renovado["access_token"]) as cliente:
                resultado = await cliente.call_tool("minha_lista_espera", {})
                assert not resultado.is_error, resultado
        asyncio.run(ler_com_token_renovado())
        print("Refresh rotativo: novo Bearer aceito pelo cliente MCP, anterior recusado: OK")
        exigir(browser.get("/api/autenticacao/csrf"), 200)
        exigir(browser.post("/oauth/revoke", data={"client_id": client_id},
                            headers={"X-XSRF-TOKEN": browser.cookies["XSRF-TOKEN"]}), 204)
        asyncio.run(token_revogado(jwt))
        asyncio.run(token_revogado(renovado["access_token"]))
        assert exigir(httpx.post(BACKEND + "/oauth/token", data={
            "grant_type": "refresh_token", "refresh_token": renovado["refresh_token"],
            "client_id": client_id}, timeout=15), 400).json()["error"] == "invalid_grant"
    finally:
        browser.close()


if __name__ == "__main__":
    main()
