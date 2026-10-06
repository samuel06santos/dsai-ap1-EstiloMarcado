import type { ParamMap } from '@angular/router';

export interface RascunhoAgendamento {
  unidadeId: number;
  servicoId: number;
  profissionalId: number;
  preferenciaId: number | null;
  inicio: string;
  criadoEm: number;
}

const VIDA_RASCUNHO_MS = 30 * 60 * 1000;

function idPublico(valor: string | null): number | null {
  if (!valor || !/^[1-9]\d*$/.test(valor)) return null;
  const id = Number(valor);
  return Number.isSafeInteger(id) ? id : null;
}

function instanteLocalValido(valor: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/.test(valor)) return false;
  const [data, hora] = valor.split('T');
  const [ano, mes, dia] = data.split('-').map(Number);
  const [horas, minutos, segundos = 0] = hora.split(':').map(Number);
  const real = new Date(Date.UTC(ano, mes - 1, dia));
  return real.getUTCFullYear() === ano && real.getUTCMonth() + 1 === mes
    && real.getUTCDate() === dia && horas < 24 && minutos < 60 && segundos < 60;
}

export function lerRascunho(unidade: string | null,
                            params: Pick<ParamMap, 'get' | 'getAll'>): RascunhoAgendamento | null {
  const unidadeId = idPublico(unidade);
  const servicoId = idPublico(params.get('servicoId'));
  const profissionalId = idPublico(params.get('profissionalId'));
  const preferenciaBruta = params.get('preferenciaId');
  const preferenciaId = preferenciaBruta === null ? null : idPublico(preferenciaBruta);
  const inicio = params.get('inicio') ?? '';
  const criadoEmBruto = params.get('criadoEm');
  const criadoEm = criadoEmBruto && /^\d{13}$/.test(criadoEmBruto) ? Number(criadoEmBruto) : NaN;
  if (!unidadeId || !servicoId || !profissionalId || (preferenciaBruta !== null && !preferenciaId)
      || (preferenciaId !== null && preferenciaId !== profissionalId)
      || !instanteLocalValido(inicio) || !Number.isSafeInteger(criadoEm)
      || criadoEm > Date.now() + 60_000 || Date.now() - criadoEm > VIDA_RASCUNHO_MS
      || ['servicoId', 'profissionalId', 'preferenciaId', 'inicio', 'criadoEm']
        .some(chave => params.getAll(chave).length > 1)) return null;
  return { unidadeId, servicoId, profissionalId, preferenciaId, inicio, criadoEm };
}

export function retornoRevisaoSeguro(retorno: string | null): string | null {
  if (!retorno || !retorno.startsWith('/') || retorno.startsWith('//') || retorno.includes('\\')) return null;
  try {
    const url = new URL(retorno, 'https://estilomarcado.invalid');
    if (url.origin !== 'https://estilomarcado.invalid'
        || !/^\/unidades\/[1-9]\d*\/revisar$/.test(url.pathname) || url.hash) return null;
    const permitido = new Set(['servicoId', 'profissionalId', 'preferenciaId', 'inicio', 'criadoEm']);
    let parametroInvalido = false;
    url.searchParams.forEach((_, chave) => { if (!permitido.has(chave)) parametroInvalido = true; });
    if (parametroInvalido) return null;
    const parts = url.pathname.split('/');
    const params = {
      get: (chave: string) => url.searchParams.get(chave),
      getAll: (chave: string) => url.searchParams.getAll(chave)
    };
    return lerRascunho(parts[2], params) ? url.pathname + url.search : null;
  } catch { return null; }
}

export function chaveIdempotencia(rascunho: RascunhoAgendamento): string {
  const identificador = `agendamento:${rascunho.unidadeId}:${rascunho.servicoId}:`
    + `${rascunho.profissionalId}:${rascunho.inicio}`;
  try {
    const anterior = sessionStorage.getItem(identificador);
    if (anterior) {
      const salvo = JSON.parse(anterior) as { chave?: string; criadoEm?: number };
      if (typeof salvo.chave === 'string' && /^[0-9a-f-]{36}$/i.test(salvo.chave)
          && typeof salvo.criadoEm === 'number' && Date.now() - salvo.criadoEm < VIDA_RASCUNHO_MS) {
        return salvo.chave;
      }
    }
  } catch { /* A repetição na mesma tela ainda reutiliza a chave em memória. */ }
  const chave = crypto.randomUUID();
  try { sessionStorage.setItem(identificador, JSON.stringify({ chave, criadoEm: Date.now() })); }
  catch { /* sessionStorage pode estar indisponível no navegador. */ }
  return chave;
}

export function limparChaveIdempotencia(rascunho: RascunhoAgendamento): void {
  try {
    sessionStorage.removeItem(`agendamento:${rascunho.unidadeId}:${rascunho.servicoId}:`
      + `${rascunho.profissionalId}:${rascunho.inicio}`);
  } catch { /* Nenhum dado pessoal depende deste armazenamento. */ }
}
