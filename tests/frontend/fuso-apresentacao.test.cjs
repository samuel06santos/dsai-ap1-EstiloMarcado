const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');
const typescript = require(require.resolve('typescript', { paths: [path.resolve(__dirname, '../../src/frontend')] }));

const arquivo = path.resolve(__dirname, '../../src/frontend/src/app/fuso-apresentacao.ts');
const compilado = typescript.transpileModule(readFileSync(arquivo, 'utf8'), {
  compilerOptions: { module: typescript.ModuleKind.CommonJS, target: typescript.ScriptTarget.ES2022 }
}).outputText;
const modulo = new Module(arquivo, module);
modulo.filename = arquivo;
modulo.paths = module.paths;
modulo._compile(compilado, arquivo);
const { formatarFuso } = modulo.exports;

test('mostra o deslocamento UTC da data local do agendamento', () => {
  assert.equal(formatarFuso('America/Sao_Paulo', '2026-10-06T14:00:00'), 'UTC-3');
  assert.equal(formatarFuso('America/Manaus', '2026-10-06'), 'UTC-4');
  assert.equal(formatarFuso('Asia/Kolkata', '2026-10-06T14:00'), 'UTC+5:30');
  assert.equal(formatarFuso('Etc/UTC', '2026-10-06'), 'UTC+0');
});

test('acompanha a mudança sazonal de deslocamento quando existe', () => {
  assert.equal(formatarFuso('America/New_York', '2026-01-15T10:00'), 'UTC-5');
  assert.equal(formatarFuso('America/New_York', '2026-07-15T10:00'), 'UTC-4');
});

test('preserva identificador inválido em vez de mostrar um deslocamento incorreto', () => {
  assert.equal(formatarFuso('America/Zona_Inexistente', '2026-10-06'),
    'America/Zona_Inexistente');
});
