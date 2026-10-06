const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');
const typescript = require(require.resolve('typescript', { paths: [path.resolve(__dirname, '../../src/frontend')] }));

const arquivo = path.resolve(__dirname, '../../src/frontend/src/app/periodo-agenda.ts');
const compilado = typescript.transpileModule(readFileSync(arquivo, 'utf8'), {
  compilerOptions: { module: typescript.ModuleKind.CommonJS, target: typescript.ScriptTarget.ES2022 }
}).outputText;
const modulo = new Module(arquivo, module);
modulo.filename = arquivo;
modulo.paths = module.paths;
modulo._compile(compilado, arquivo);
const { dividirPeriodoAgenda } = modulo.exports;

test('grade mensal de 42 dias é dividida em consultas aceitas pela API', () => {
  assert.deepEqual(dividirPeriodoAgenda('2026-09-28', '2026-11-08'), [
    { de: '2026-09-28', ate: '2026-10-28' },
    { de: '2026-10-29', ate: '2026-11-08' }
  ]);
  assert.deepEqual(dividirPeriodoAgenda('2026-10-06', '2026-10-06'), [
    { de: '2026-10-06', ate: '2026-10-06' }
  ]);
  assert.deepEqual(dividirPeriodoAgenda('2026-11-08', '2026-09-28'), []);
});
