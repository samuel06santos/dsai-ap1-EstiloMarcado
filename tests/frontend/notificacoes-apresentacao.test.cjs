const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');
const typescript = require(require.resolve('typescript', { paths: [path.resolve(__dirname, '../../src/frontend')] }));

const arquivo = path.resolve(__dirname, '../../src/frontend/src/app/notificacoes-apresentacao.ts');
const compilado = typescript.transpileModule(readFileSync(arquivo, 'utf8'), {
  compilerOptions: { module: typescript.ModuleKind.CommonJS, target: typescript.ScriptTarget.ES2022 }
}).outputText;
const modulo = new Module(arquivo, module);
modulo.filename = arquivo;
modulo.paths = module.paths;
modulo._compile(compilado, arquivo);
const { destinoNotificacao, ordenarNotificacoes, resumoNotificacao, tituloNotificacao } = modulo.exports;

test('somente referências válidas geram rotas diretas da própria área', () => {
  assert.deepEqual(destinoNotificacao({ referenciaTipo: 'AGENDAMENTO', referenciaId: 42 }),
    { tipo: 'AGENDAMENTO', url: '/agendamentos/42/historico' });
  assert.deepEqual(destinoNotificacao({ referenciaTipo: 'OFERTA', referenciaId: 9 }),
    { tipo: 'OFERTA', url: '/lista-espera?ofertaId=9' });
  for (const referenciaId of [0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1, NaN]) {
    assert.equal(destinoNotificacao({ referenciaTipo: 'AGENDAMENTO', referenciaId }), null);
  }
  assert.equal(destinoNotificacao({ referenciaTipo: 'OUTRA', referenciaId: 42 }), null);
});

test('títulos desconhecidos continuam legíveis e sem dados inventados', () => {
  const aviso = { tipo: 'TIPO_NOVO', referenciaTipo: 'OUTRA', referenciaId: 1 };
  assert.equal(tituloNotificacao(aviso), 'Atualização da sua conta');
  assert.equal(resumoNotificacao(aviso), 'Veja as novidades da sua conta.');
  assert.equal(tituloNotificacao({ ...aviso, tipo: 'OFERTA' }), 'Vaga disponível');
});

test('ordena por instante e depois por id sem alterar a resposta original', () => {
  const itens = [
    { id: 2, criadoEm: '2026-10-06T10:00:00Z' },
    { id: 1, criadoEm: '2026-10-06T11:00:00Z' },
    { id: 3, criadoEm: '2026-10-06T11:00:00Z' }
  ];
  assert.deepEqual(ordenarNotificacoes(itens).map(item => item.id), [3, 1, 2]);
  assert.deepEqual(itens.map(item => item.id), [2, 1, 3]);
});
