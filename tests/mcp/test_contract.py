"""Contrato estático das tools, independente da disponibilidade do servidor remoto."""

import ast
import base64
import json
import pathlib
from datetime import date
import hashlib
import hmac
import time
import uuid
import tomllib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
TOOLS = {
    "listar_filiais", "listar_servicos", "consultar_horarios", "meus_agendamentos",
    "detalhar_agendamento", "horarios_para_reagendar", "minha_lista_espera",
    "criar_agendamento", "cancelar_agendamento", "reagendar_agendamento",
}
WRITES = {"criar_agendamento", "cancelar_agendamento", "reagendar_agendamento"}


class McpContractTest(unittest.TestCase):
    def test_docstrings_and_business_only_arguments(self):
        tree = ast.parse((ROOT / "src/mcp/server.py").read_text(encoding="utf-8"))
        found = {node.name: node for node in tree.body if isinstance(node, ast.AsyncFunctionDef)
                 and node.name in TOOLS}
        self.assertEqual(TOOLS, found.keys())
        for name, node in found.items():
            description = ast.get_docstring(node)
            self.assertIsNotNone(description, name)
            self.assertIn("Use", description, name)
            args = {arg.arg for arg in node.args.args}
            self.assertFalse(args & {"user_id", "cliente_id", "email", "senha", "cookie", "jwt", "token"}, name)
            if name in WRITES:
                self.assertIn("confirm", description.lower(), name)

    def test_versioned_client_and_approval_rules(self):
        client = json.loads((ROOT / ".mcp.json").read_text(encoding="utf-8"))
        self.assertEqual("http", client["mcpServers"]["estilo_marcado"]["type"])
        self.assertTrue(client["mcpServers"]["estilo_marcado"]["url"].startswith("https://"))
        permissions = json.loads((ROOT / ".claude/settings.json").read_text(encoding="utf-8"))["permissions"]
        self.assertEqual({f"mcp__estilo_marcado__{name}" for name in WRITES}, set(permissions["ask"]))
        self.assertEqual({f"mcp__estilo_marcado__{name}" for name in TOOLS - WRITES}, set(permissions["allow"]))

        opencode = json.loads((ROOT / "opencode.json").read_text(encoding="utf-8"))
        self.assertEqual(client["mcpServers"]["estilo_marcado"]["url"],
                         opencode["mcp"]["estilo_marcado"]["url"])
        regras = opencode["permission"]
        self.assertEqual("ask", regras["estilo_marcado_*"])
        self.assertEqual({f"estilo_marcado_{name}" for name in TOOLS - WRITES},
                         {name for name, mode in regras.items() if mode == "allow"})

        codex = tomllib.loads((ROOT / ".codex/config.toml").read_text(encoding="utf-8"))
        servidor = codex["mcp_servers"]["estilo_marcado"]
        self.assertEqual(client["mcpServers"]["estilo_marcado"]["url"], servidor["url"])
        self.assertEqual("prompt", servidor["default_tools_approval_mode"])
        self.assertEqual(TOOLS - WRITES, set(servidor["tools"]))
        self.assertTrue(all(item["approval_mode"] == "approve"
                            for item in servidor["tools"].values()))

    def test_confirmation_nonce_is_bound_to_intent_and_bearer(self):
        tree = ast.parse((ROOT / "src/mcp/server.py").read_text(encoding="utf-8"))
        helpers = [node for node in tree.body if isinstance(node, ast.FunctionDef)
                   and node.name in {"_assinar_intencao", "_validar_intencao"}]
        namespace = {"base64": base64, "hashlib": hashlib, "hmac": hmac,
                     "json": json, "time": time, "uuid": uuid, "SERVICE_KEY": "test-key"}
        exec(compile(ast.Module(body=helpers, type_ignores=[]), "server.py", "exec"), namespace)
        state = namespace["_assinar_intencao"]("criar|1|2|2026-10-20T09:00", "Bearer um")
        self.assertRegex(namespace["_validar_intencao"](state, "criar|1|2|2026-10-20T09:00",
                                                         "Bearer um"), r"^[0-9a-f-]{36}$")
        with self.assertRaises(ValueError):
            namespace["_validar_intencao"](state, "criar|1|2|2026-10-20T09:15", "Bearer um")
        with self.assertRaises(ValueError):
            namespace["_validar_intencao"](state, "criar|1|2|2026-10-20T09:00", "Bearer outro")

    def test_confirmation_is_mrtr_and_not_a_tool_argument(self):
        tree = ast.parse((ROOT / "src/mcp/server.py").read_text(encoding="utf-8"))
        source = (ROOT / "src/mcp/server.py").read_text(encoding="utf-8")
        self.assertIn("InputRequiredResult", source)
        self.assertIn('input_requests={"confirmacao": ElicitRequest(params=params)}', source)
        self.assertNotIn("Resolve(", source)
        self.assertNotIn("Annotated", source)
        found = {node.name: node for node in tree.body if isinstance(node, ast.AsyncFunctionDef)
                 and node.name in WRITES}
        for name, node in found.items():
            self.assertNotIn("confirmacao", {arg.arg for arg in node.args.args}, name)
        self.assertIn('transport="streamable-http"', source)
        self.assertIn("stateless_http=False", source)

    def test_bad_backend_request_preserves_validation_message(self):
        source = (ROOT / "src/mcp/server.py").read_text(encoding="utf-8")
        self.assertIn("if response.status_code == 400:", source)
        self.assertIn('payload.get("mensagem")', source)

    def test_agendamentos_period_is_limited_to_31_days_and_date_only(self):
        tree = ast.parse((ROOT / "src/mcp/server.py").read_text(encoding="utf-8"))
        helper = next(node for node in tree.body if isinstance(node, ast.FunctionDef)
                      and node.name == "_periodo_agendamentos")
        namespace = {"date": date}
        exec(compile(ast.Module(body=[helper], type_ignores=[]), "server.py", "exec"), namespace)
        self.assertEqual((date(2026, 10, 20), date(2026, 10, 31)),
                         namespace["_periodo_agendamentos"]("2026-10-20", "2026-10-31", 0, 50))
        with self.assertRaises(ValueError):
            namespace["_periodo_agendamentos"]("2026-10-20", "2026-11-20", 0, 50)
        with self.assertRaises(ValueError):
            namespace["_periodo_agendamentos"]("2026-10-20T00:00:00", "2026-10-21", 0, 50)

    def test_private_modern_call_gets_transport_challenge(self):
        tree = ast.parse((ROOT / "src/mcp/server.py").read_text(encoding="utf-8"))
        challenge = next(node for node in tree.body if isinstance(node, ast.ClassDef)
                         and node.name == "AuthChallenge")
        sent = []

        class Response:
            def __init__(self, body, status_code=200, headers=None):
                self.body, self.status_code, self.headers = body, status_code, headers

            async def __call__(self, scope, receive, send):
                await send({"status": self.status_code, "body": self.body, "headers": self.headers})

        async def inner(scope, receive, send):
            self.assertEqual("tools/call", json.loads((await receive())["body"])["method"])
            await send({"status": 200})

        namespace = {"JSONResponse": Response, "ISSUER": "https://example.test",
                     "AUDIENCE": "https://example.test/mcp", "json": json,
                     "PRIVATE_READS": {"meus_agendamentos"}, "PRIVATE_WRITES": {"cancelar_agendamento"}}
        async def invalidar(_value):
            raise ValueError("token expirado")
        namespace["_validar_token"] = invalidar
        exec(compile(ast.Module(body=[challenge], type_ignores=[]), "server.py", "exec"), namespace)
        middleware = namespace["AuthChallenge"](inner)
        scope = {"type": "http", "method": "POST", "path": "/mcp",
                 "headers": [(b"content-type", b"application/json")]}
        private = {"type": "http.request", "body": json.dumps({"method": "tools/call",
                   "params": {"name": "meus_agendamentos"}}).encode(), "more_body": False}
        _run_immediate(middleware(scope, lambda: _event(private), lambda event: _append(sent, event)))
        self.assertEqual(401, sent[-1]["status"])
        self.assertIn("resource_metadata", sent[-1]["headers"]["WWW-Authenticate"])
        sent.clear()
        public = {"type": "http.request", "body": json.dumps({"method": "tools/call",
                  "params": {"name": "listar_filiais"}}).encode(), "more_body": False}
        _run_immediate(middleware(scope, lambda: _event(public), lambda event: _append(sent, event)))
        self.assertEqual(200, sent[-1]["status"])
        sent.clear()
        metadata = {**scope, "method": "GET", "path": "/.well-known/oauth-protected-resource/mcp"}
        _run_immediate(middleware(metadata, lambda: _event(public), lambda event: _append(sent, event)))
        self.assertEqual(200, sent[-1]["status"])
        self.assertEqual("https://example.test/mcp", sent[-1]["body"]["resource"])
        sent.clear()
        expirado = {**scope, "headers": [(b"authorization", b"Bearer expirado")]}
        _run_immediate(middleware(expirado, lambda: _event(public), lambda event: _append(sent, event)))
        self.assertEqual(401, sent[-1]["status"])
        self.assertIn('error="invalid_token"', sent[-1]["headers"]["WWW-Authenticate"])

    def test_oauth_css_routes_to_backend_before_static_assets(self):
        frontend_nginx = (ROOT / "src/frontend/nginx/default.conf").read_text(encoding="utf-8")
        self.assertIn("location ^~ /oauth/ {", frontend_nginx)
        self.assertIn("location /oauth/ {", (ROOT / "deploy/nginx/sites-available"
                        / "estilomarcado.samuelsantos.qzz.io.conf").read_text(encoding="utf-8"))


async def _append(destination, event):
    destination.append(event)


async def _event(event):
    return event


def _run_immediate(coroutine):
    """Executa a cadeia ASGI sem I/O, independente do loop do host Windows."""
    try:
        coroutine.send(None)
    except StopIteration:
        return
    raise AssertionError("A resposta ASGI de teste não deveria suspender")


if __name__ == "__main__":
    unittest.main()
