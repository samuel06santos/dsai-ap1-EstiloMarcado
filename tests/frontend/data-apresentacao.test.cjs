const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');
const typescript = require(require.resolve('typescript', { paths: [path.resolve(__dirname, '../../src/frontend')] }));

const arquivo = path.resolve(__dirname, '../../src/frontend/src/app/data-apresentacao.ts');
const fonte = readFileSync(arquivo, 'utf8');
const compilado = typescript.transpileModule(fonte, {
  compilerOptions: { module: typescript.ModuleKind.CommonJS, target: typescript.ScriptTarget.ES2022 }
}).outputText;
const modulo = new Module(arquivo, module);
modulo.filename = arquivo;
modulo.paths = module.paths;
modulo._compile(compilado, arquivo);
const { formatarData, formatarDataHora } = modulo.exports;

test('datas locais aparecem em DD/MM/YYYY sem conversão de fuso', () => {
  assert.equal(formatarData('2026-10-06'), '06/10/2026');
  assert.equal(formatarDataHora('2026-10-06T00:30:00'), '06/10/2026 às 00:30');
  assert.equal(formatarDataHora('2026-10-06T23:45:00'), '06/10/2026 às 23:45');
});
