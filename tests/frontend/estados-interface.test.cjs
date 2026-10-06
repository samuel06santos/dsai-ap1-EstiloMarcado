const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');
const typescript = require(require.resolve('typescript', { paths: [path.resolve(__dirname, '../../src/frontend')] }));

const arquivo = path.resolve(__dirname, '../../src/frontend/src/app/estados-interface.ts');
const compilado = typescript.transpileModule(readFileSync(arquivo, 'utf8'), {
  compilerOptions: { module: typescript.ModuleKind.CommonJS, target: typescript.ScriptTarget.ES2022 }
}).outputText;
const modulo = new Module(arquivo, module);
modulo.filename = arquivo;
modulo.paths = module.paths;
modulo._compile(compilado, arquivo);
const {
  classificarLista, idUrlValido, selecaoValida, resumoFiltros,
  parametrosFiltrosUrl, orientacaoVazia
} = modulo.exports;

test('falha de carregamento tem precedência e não é tratada como lista vazia', () => {
  assert.equal(classificarLista({ carregando: true, erro: true, total: 0, filtrosAtivos: 0 }), 'erro');
  assert.equal(classificarLista({ carregando: true, erro: false, total: 0, filtrosAtivos: 0 }), 'carregando');
  assert.equal(classificarLista({ carregando: false, erro: false, total: 0, filtrosAtivos: 0 }), 'sem-dados');
  assert.equal(classificarLista({ carregando: false, erro: false, total: 0, filtrosAtivos: 2 }), 'sem-resultado');
  assert.equal(classificarLista({ carregando: false, erro: false, total: 3, filtrosAtivos: 2 }), 'conteudo');
});

test('identificadores adulterados na URL não ampliam a seleção', () => {
  assert.equal(idUrlValido('10'), 10);
  assert.equal(idUrlValido('0'), null);
  assert.equal(idUrlValido('-3'), null);
  assert.equal(idUrlValido('1.5'), null);
  assert.equal(idUrlValido('10abc'), null);
  assert.equal(idUrlValido(''), null);
  assert.equal(idUrlValido(null), null);
  const opcoes = [{ id: 10 }, { id: 11 }];
  assert.equal(selecaoValida('10', opcoes), 10);
  assert.equal(selecaoValida('99', opcoes), null);
  assert.equal(selecaoValida('abc', opcoes), null);
});

test('filtros ativos viram resumo legível e a URL omite seleções vazias', () => {
  assert.equal(resumoFiltros([
    { chave: 'unidadeId', rotulo: 'Filial', valor: 'Centro' },
    { chave: 'status', rotulo: 'Estado', valor: 'Confirmado' }
  ]), 'Filial: Centro · Estado: Confirmado');
  assert.equal(resumoFiltros([]), '');
  assert.deepEqual(parametrosFiltrosUrl({ data: '2026-10-06', profissionalId: 12, status: '' }),
    { data: '2026-10-06', profissionalId: '12' });
  assert.deepEqual(parametrosFiltrosUrl({ a: null, b: undefined, c: '' }), {});
});

test('cada contexto vazio tem orientação curta e apenas ações permitidas', () => {
  const contextos = ['cliente-sem-agendamento', 'recepcao-sem-atendimentos',
    'profissional-sem-agenda', 'lista-espera-cliente', 'lista-espera-equipe',
    'notificacoes', 'admin-sem-profissional', 'admin-sem-servico'];
  const destinos = new Set(['filiais', 'unidades', 'disponibilidade', 'agendar', 'periodo',
    'preferencias', 'cadastro-profissional', 'cadastro-servico']);
  for (const contexto of contextos) {
    const orientacao = orientacaoVazia(contexto);
    assert.ok(orientacao.titulo.length > 0, `${contexto} precisa de título`);
    assert.ok(orientacao.descricao.length > 0, `${contexto} precisa de descrição`);
    for (const acao of orientacao.acoes) {
      assert.ok(destinos.has(acao.destino), `${contexto}: destino inválido ${acao.destino}`);
    }
  }

  assert.deepEqual(orientacaoVazia('recepcao-sem-atendimentos').acoes.map(a => a.destino),
    ['agendar', 'periodo']);
  assert.deepEqual(orientacaoVazia('notificacoes').acoes.map(a => a.destino), ['preferencias']);
  assert.deepEqual(orientacaoVazia('admin-sem-profissional').acoes.map(a => a.destino),
    ['cadastro-profissional']);
  assert.deepEqual(orientacaoVazia('admin-sem-servico').acoes.map(a => a.destino),
    ['cadastro-servico']);
});

test('perfis sem permissão não recebem atalho de criação de reserva', () => {
  const profissional = orientacaoVazia('profissional-sem-agenda').acoes.map(a => a.destino);
  assert.ok(profissional.includes('disponibilidade'));
  assert.ok(!profissional.includes('agendar'),
    'o perfil profissional não cria reserva a partir do estado vazio');

  assert.equal(orientacaoVazia('lista-espera-equipe').acoes.length, 0,
    'a equipe mantém os filtros em vez de receber atalho paralelo');
});
