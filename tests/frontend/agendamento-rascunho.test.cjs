const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');
const typescript = require(require.resolve('typescript', { paths: [path.resolve(__dirname, '../../src/frontend')] }));

const arquivo = path.resolve(__dirname, '../../src/frontend/src/app/agendamento-rascunho.ts');
const fonte = readFileSync(arquivo, 'utf8');
const compilado = typescript.transpileModule(fonte, {
  compilerOptions: { module: typescript.ModuleKind.CommonJS, target: typescript.ScriptTarget.ES2022 }
}).outputText;
const modulo = new Module(arquivo, module);
modulo.filename = arquivo;
modulo.paths = module.paths;
modulo._compile(compilado, arquivo);
const { lerRascunho, retornoRevisaoSeguro, chaveIdempotencia, limparChaveIdempotencia } = modulo.exports;

function parametros(valores) {
  const url = new URLSearchParams(valores);
  return { get: chave => url.get(chave), getAll: chave => url.getAll(chave) };
}

function escolha(criadoEm = Date.now()) {
  return parametros({ servicoId: '20', profissionalId: '30', inicio: '2026-10-20T14:00:00',
    criadoEm: String(criadoEm) });
}

test('aceita somente rascunho recente e com IDs e data válidos', () => {
  assert.equal(lerRascunho('10', escolha())?.profissionalId, 30);
  assert.equal(lerRascunho('10', escolha(Date.now() - 31 * 60_000)), null);
  assert.equal(lerRascunho('10', parametros({ servicoId: '20', profissionalId: '30',
    inicio: '2026-02-30T14:00:00', criadoEm: String(Date.now()) })), null);
  assert.equal(lerRascunho('10', parametros('servicoId=20&servicoId=21&profissionalId=30'
    + '&inicio=2026-10-20T14%3A00%3A00&criadoEm=' + Date.now())), null);
});

test('retorno após autenticação aceita somente revisão interna válida', () => {
  const interno = '/unidades/10/revisar?servicoId=20&profissionalId=30'
    + '&inicio=2026-10-20T14%3A00%3A00&criadoEm=' + Date.now();
  assert.equal(retornoRevisaoSeguro(interno), interno);
  assert.equal(retornoRevisaoSeguro('https://evil.example' + interno), null);
  assert.equal(retornoRevisaoSeguro('//evil.example' + interno), null);
  assert.equal(retornoRevisaoSeguro('/administracao/estabelecimento'), null);
  assert.equal(retornoRevisaoSeguro(interno + '&token=abc'), null);
});

test('repetição usa a mesma chave e conclusão a limpa', () => {
  const dados = { unidadeId: 10, servicoId: 20, profissionalId: 30,
    preferenciaId: null, inicio: '2026-10-20T14:00:00', criadoEm: Date.now() };
  const memoria = new Map();
  global.sessionStorage = {
    getItem: chave => memoria.get(chave) ?? null,
    setItem: (chave, valor) => memoria.set(chave, valor),
    removeItem: chave => memoria.delete(chave)
  };
  global.crypto = require('node:crypto').webcrypto;
  const primeira = chaveIdempotencia(dados);
  assert.match(primeira, /^[0-9a-f-]{36}$/);
  assert.equal(chaveIdempotencia(dados), primeira);
  limparChaveIdempotencia(dados);
  assert.notEqual(chaveIdempotencia(dados), primeira);
});
