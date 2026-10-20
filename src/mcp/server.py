"""Recurso MCP remoto do Estilo Marcado; a identidade vem somente da requisição HTTP."""

import hashlib
import hmac
import json
import os
import uuid
from datetime import date
from typing import Annotated

import httpx
import jwt
from fastmcp import Context, FastMCP
from fastmcp.server.dependencies import get_http_request
from mcp.server.mcpserver import Elicit, Resolve
from pydantic import BaseModel
from starlette.responses import JSONResponse


BACKEND = os.environ.get("MCP_BACKEND_URL", "http://backend:8080").rstrip("/")
ISSUER = os.environ.get("MCP_ISSUER", "http://localhost:8080").rstrip("/")
AUDIENCE = os.environ.get("MCP_AUDIENCE", "http://127.0.0.1:8000/mcp")
SERVICE_KEY = os.environ.get("MCP_SERVICE_KEY", "")
mcp = FastMCP("Estilo Marcado")
PRIVATE_READS = {"meus_agendamentos", "detalhar_agendamento", "horarios_para_reagendar",
                 "minha_lista_espera"}
PRIVATE_WRITES = {"cancelar_agendamento", "reagendar_agendamento"}


class Confirmacao(BaseModel):
    """Resposta estruturada da aprovação humana de uma operação de escrita."""

    aprovar: bool


def _confirmacao_nonce(ctx: Context, intencao: str, bearer: str | None) -> str:
    """Gera o nonce da escrita a partir do estado da confirmação do MCP.

    Em conexões modernas, o FastMCP devolve a pergunta e recebe a resposta em
    outra requisição; ``request_state`` permanece estável entre essas rodadas.
    Em clientes legados, a resposta chega na mesma sessão e um valor aleatório
    é suficiente. O HMAC impede que o cliente fabrique um nonce aceito pelo
    backend fora de uma confirmação emitida pelo servidor.
    """
    if not SERVICE_KEY:
        raise ValueError("Servidor MCP sem chave de serviço configurada.")
    estado = ctx.request_state
    if estado is None:
        sessao = get_http_request().headers.get("mcp-session-id")
        estado = f"legacy|{sessao or uuid.uuid4()}"
    elif not isinstance(estado, str):
        estado = json.dumps(estado, sort_keys=True, separators=(",", ":"))
    material = "\0".join((intencao, bearer or "", estado))
    digest = hmac.new(SERVICE_KEY.encode(), material.encode(), hashlib.sha256).hexdigest()
    return str(uuid.UUID(digest[:32]))


async def _confirmar_criacao(filial_id: int, servico_id: int, inicio: str, ctx: Context) -> Elicit[Confirmacao]:
    return Elicit(
        f"Criar agendamento na filial {filial_id}, serviço {servico_id}, início {inicio}; "
        "o servidor escolhe o primeiro profissional disponível por ID. Sem autorização MCP, "
        "será criada apenas pré-reserva de dez minutos para checkout. Confirma?",
        Confirmacao,
    )


async def _confirmar_cancelamento(agendamento_id: int, motivo: str, ctx: Context) -> Elicit[Confirmacao]:
    return Elicit(
        f"Cancelar o agendamento {agendamento_id}? Motivo: {motivo or 'não informado'}.",
        Confirmacao,
    )


async def _confirmar_reagendamento(agendamento_id: int, inicio: str, ctx: Context) -> Elicit[Confirmacao]:
    return Elicit(f"Reagendar o agendamento {agendamento_id} para {inicio}?", Confirmacao)


async def _bearer() -> str | None:
    """Resolve o Bearer da requisição MCP atual, sem estado entre chamadas."""
    request = get_http_request()
    value = request.headers.get("authorization")
    if value is None:
        return None
    return await _validar_token(value)


async def _validar_token(value: str) -> str:
    """Valida o acesso apresentado no HTTP; refresh pertence ao cliente OAuth."""
    if not value.startswith("Bearer ") or len(value) < 20:
        raise ValueError("Bearer MCP inválido; reautorize o cliente MCP.")
    token = value[7:]
    async with httpx.AsyncClient(timeout=10) as client:
        response = await client.get(f"{BACKEND}/oauth/jwks")
        response.raise_for_status()
        jwks = response.json()
    try:
        header = jwt.get_unverified_header(token)
        if header.get("alg") != "RS256":
            raise ValueError("algoritmo inválido")
        keys = [key for key in jwks["keys"] if key.get("kid") == header.get("kid")]
        if len(keys) != 1:
            raise ValueError("chave inválida")
        public_key = jwt.algorithms.RSAAlgorithm.from_jwk(keys[0])
        jwt.decode(token, public_key, algorithms=["RS256"], issuer=ISSUER,
                   audience=AUDIENCE, options={"require": ["iss", "aud", "sub", "iat", "nbf", "exp", "jti", "client_id", "scope"]})
    except (jwt.PyJWTError, ValueError, KeyError) as exc:
        raise ValueError("Bearer MCP inválido ou expirado; reautorize o cliente MCP.") from exc
    return value


async def _api(method: str, path: str, *, params: dict | None = None, body: dict | None = None):
    bearer = await _bearer()
    headers = {"X-MCP-Service-Key": SERVICE_KEY}
    if bearer:
        headers["Authorization"] = bearer
    async with httpx.AsyncClient(timeout=20) as client:
        response = await client.request(method, f"{BACKEND}/api/mcp/internal{path}",
                                        params=params, json=body, headers=headers)
    if response.status_code == 401:
        raise ValueError("Autenticação MCP inválida ou expirada; reautorize o cliente MCP.")
    if response.status_code == 403:
        raise ValueError("A conta ou o escopo MCP não permite esta operação.")
    if response.status_code >= 400:
        raise ValueError(f"Operação indisponível ({response.status_code}).")
    return response.json()


@mcp.tool()
async def listar_filiais(busca: str = "", pagina: int = 1, tamanho: int = 12):
    """Use quando o usuário quiser conhecer ou escolher uma filial. Comece aqui se não houver filial definida. Consulta pública; retorna filiais ativas e informações públicas, sem alterar a agenda."""
    return await _api("GET", "/filiais", params={"busca": busca, "pagina": pagina, "tamanho": tamanho})


@mcp.tool()
async def listar_servicos(filial_id: int):
    """Use quando o usuário quiser conhecer serviços e preços de uma filial ou escolher um serviço antes de buscar horário. Consulta pública; não reserva."""
    return await _api("GET", f"/filiais/{filial_id}/servicos")


@mcp.tool()
async def consultar_horarios(filial_id: int, servico_id: int, data: str):
    """Use para ver disponibilidade de filial, serviço e data local AAAA-MM-DD. Retorna horários e fuso IANA; é uma prévia e não reserva horário."""
    date.fromisoformat(data)
    return await _api("GET", f"/filiais/{filial_id}/servicos/{servico_id}/horarios", params={"data": data})


@mcp.tool()
async def meus_agendamentos(de: str, ate: str, status: str = "", pagina: int = 0, tamanho: int = 50):
    """Use para listar reservas próprias futuras ou passadas por período, estado e página. É somente leitura. Sem Bearer, devolve AUTENTICACAO_MCP_NECESSARIA: autorize o cliente MCP e repita, sem passar credenciais como argumento."""
    date.fromisoformat(de); date.fromisoformat(ate)
    params = {"de": de, "ate": ate, "pagina": pagina, "tamanho": tamanho}
    if status:
        params["status"] = status
    return await _api("GET", "/me/agendamentos", params=params)


@mcp.tool()
async def detalhar_agendamento(agendamento_id: int):
    """Use para consultar detalhes de um agendamento próprio identificado por ID. Não tente descobrir reservas de terceiros. Sem Bearer, devolve AUTENTICACAO_MCP_NECESSARIA; autorize o cliente MCP."""
    return await _api("GET", f"/agendamentos/{agendamento_id}")


@mcp.tool()
async def horarios_para_reagendar(agendamento_id: int, data: str):
    """Use para comparar novos horários de um agendamento próprio antes de decidir reagendar. Consulta sem mudança. Sem Bearer, devolve AUTENTICACAO_MCP_NECESSARIA; autorize o cliente MCP."""
    date.fromisoformat(data)
    return await _api("GET", f"/agendamentos/{agendamento_id}/horarios-reagendamento", params={"data": data})


@mcp.tool()
async def minha_lista_espera(pagina: int = 0, tamanho: int = 50):
    """Use para consultar inscrições próprias na lista de espera. É somente leitura. Sem Bearer, devolve AUTENTICACAO_MCP_NECESSARIA; autorize o cliente MCP e repita."""
    return await _api("GET", "/me/lista-espera", params={"pagina": pagina, "tamanho": tamanho})


@mcp.tool()
async def criar_agendamento(filial_id: int, servico_id: int, inicio: str,
                            confirmacao: Annotated[Confirmacao, Resolve(_confirmar_criacao)],
                            ctx: Context):
    """Use somente após o usuário escolher filial, serviço e horário local e confirmar a ação no diálogo confiável do cliente MCP. Com Bearer cria reserva própria; sem Bearer cria pré-reserva por dez minutos e devolve link HTTPS para checkout no site. O servidor escolhe o primeiro profissional elegível por ID; a escolha pode mudar até a transação. Não use para simples consulta."""
    if not confirmacao.aprovar:
        raise ValueError("Operação recusada; agenda preservada.")
    bearer = await _bearer()
    key = _confirmacao_nonce(ctx, f"criar|{filial_id}|{servico_id}|{inicio}", bearer)
    return await _api("POST", "/agendamentos", body={"filialId": filial_id, "servicoId": servico_id,
                                                     "inicio": inicio, "chaveIdempotencia": key})


@mcp.tool()
async def cancelar_agendamento(
        agendamento_id: int,
        confirmacao: Annotated[Confirmacao, Resolve(_confirmar_cancelamento)],
        ctx: Context,
        motivo: str = ""):
    """Use somente quando o cliente pedir cancelar uma reserva própria. Revise ID e efeito e obtenha confirmação humana explícita pelo cliente MCP antes da escrita. Não use para consulta."""
    if not confirmacao.aprovar:
        raise ValueError("Operação recusada; agenda preservada.")
    bearer = await _bearer()
    if bearer is None:
        return {"codigo": "AUTENTICACAO_MCP_NECESSARIA",
                "mensagem": "Autorize o cliente MCP no Estilo Marcado e repita a chamada."}
    return await _api("POST", f"/agendamentos/{agendamento_id}/cancelamentos",
                      body={"motivo": motivo,
                            "confirmacaoNonce": _confirmacao_nonce(
                                ctx, f"cancelar|{agendamento_id}|{motivo}", bearer)})


@mcp.tool()
async def reagendar_agendamento(agendamento_id: int, inicio: str,
                                confirmacao: Annotated[Confirmacao, Resolve(_confirmar_reagendamento)],
                                ctx: Context):
    """Use somente após o cliente escolher novo horário para reserva própria, revisar o destino e confirmar explicitamente no cliente MCP. Consulte alternativas antes se necessário. Esta tool altera a agenda."""
    if not confirmacao.aprovar:
        raise ValueError("Operação recusada; agenda preservada.")
    bearer = await _bearer()
    if bearer is None:
        return {"codigo": "AUTENTICACAO_MCP_NECESSARIA",
                "mensagem": "Autorize o cliente MCP no Estilo Marcado e repita a chamada."}
    return await _api("POST", f"/agendamentos/{agendamento_id}/reagendamentos",
                      body={"inicio": inicio, "confirmacaoNonce": _confirmacao_nonce(
                          ctx, f"reagendar|{agendamento_id}|{inicio}", bearer)})


class AuthChallenge:
    """Desafio OAuth para clientes modernos sem Bearer; as tools antigas orientam no corpo."""

    def __init__(self, inner):
        self.inner = inner

    async def __call__(self, scope, receive, send):
        if scope["type"] == "http" and scope.get("method") == "GET" and scope.get("path") == "/healthz":
            await JSONResponse({"status": "ok"})(scope, receive, send)
            return
        if (scope["type"] == "http" and scope.get("method") == "GET"
                and scope.get("path") in {"/.well-known/oauth-protected-resource/mcp",
                                          "/.well-known/oauth-protected-resource"}):
            await JSONResponse({
                "resource": AUDIENCE,
                "authorization_servers": [ISSUER],
                "bearer_methods_supported": ["header"],
                "scopes_supported": ["mcp:read", "mcp:write"],
            }, headers={"Cache-Control": "no-store"})(scope, receive, send)
            return
        if scope["type"] == "http" and scope.get("method") == "POST" and scope.get("path", "").rstrip("/") == "/mcp":
            headers = {k.decode().lower(): v.decode() for k, v in scope.get("headers", [])}
            if headers.get("authorization"):
                try:
                    await _validar_token(headers["authorization"])
                except ValueError:
                    await JSONResponse({"codigo": "TOKEN_MCP_INVALIDO"}, status_code=401, headers={
                        "WWW-Authenticate": (f'Bearer resource_metadata="{ISSUER}/.well-known/oauth-protected-resource/mcp", '
                                             'error="invalid_token", error_description="MCP access token expired or invalid"'),
                        "Cache-Control": "no-store"})(scope, receive, send)
                    return
            if not headers.get("authorization"):
                chunks = []
                total = 0
                while True:
                    event = await receive()
                    if event["type"] != "http.request":
                        await JSONResponse({"codigo": "REQUISICAO_INCOMPLETA"}, status_code=400)(scope, receive, send)
                        return
                    part = event.get("body", b"")
                    total += len(part)
                    if total > 1024 * 1024:
                        await JSONResponse({"codigo": "REQUISICAO_EXCESSIVA"}, status_code=413)(scope, receive, send)
                        return
                    chunks.append(part)
                    if not event.get("more_body", False):
                        break
                body = b"".join(chunks)
                try:
                    chamada = json.loads(body)
                    privado = (chamada.get("method") == "tools/call"
                               and chamada.get("params", {}).get("name") in PRIVATE_READS | PRIVATE_WRITES)
                except (ValueError, AttributeError):
                    privado = False
                if privado:
                    response = JSONResponse(
                        {"codigo": "AUTENTICACAO_MCP_NECESSARIA",
                         "mensagem": "Autorize o cliente MCP no Estilo Marcado e repita a chamada."},
                        status_code=401,
                        headers={"WWW-Authenticate": f'Bearer resource_metadata="{ISSUER}/.well-known/oauth-protected-resource/mcp"',
                                 "Cache-Control": "no-store"})
                    await response(scope, receive, send)
                    return
                entregue = False

                async def repetir_corpo():
                    nonlocal entregue
                    if not entregue:
                        entregue = True
                        return {"type": "http.request", "body": body, "more_body": False}
                    return await receive()

                await self.inner(scope, repetir_corpo, send)
                return
        await self.inner(scope, receive, send)


app = AuthChallenge(mcp.http_app(path="/mcp", transport="streamable-http",
                                 json_response=False, stateless_http=False))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("MCP_PORT", "8000")))
