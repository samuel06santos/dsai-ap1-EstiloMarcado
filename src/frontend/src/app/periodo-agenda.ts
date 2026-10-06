export interface FaixaAgenda { de: string; ate: string }

/** A listagem aceita no máximo 31 dias por consulta, com ambas as pontas inclusivas. */
export function dividirPeriodoAgenda(de: string, ate: string): FaixaAgenda[] {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(de) || !/^\d{4}-\d{2}-\d{2}$/.test(ate) || de > ate) {
    return [];
  }
  const faixas: FaixaAgenda[] = [];
  let inicio = de;
  while (inicio <= ate) {
    const fim = new Date(`${inicio}T12:00:00Z`);
    fim.setUTCDate(fim.getUTCDate() + 30);
    const limite = fim.toISOString().slice(0, 10);
    const ultimo = limite < ate ? limite : ate;
    faixas.push({ de: inicio, ate: ultimo });
    if (ultimo === ate) break;
    fim.setUTCDate(fim.getUTCDate() + 1);
    inicio = fim.toISOString().slice(0, 10);
  }
  return faixas;
}
