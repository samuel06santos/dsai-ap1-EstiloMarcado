function deslocamentoMinutos(fuso: string, instante: Date): number | null {
  const nome = new Intl.DateTimeFormat('en-US', { timeZone: fuso, timeZoneName: 'shortOffset' })
    .formatToParts(instante).find(parte => parte.type === 'timeZoneName')?.value;
  if (nome === 'GMT') return 0;
  const partes = /^GMT([+-])(\d{1,2})(?::(\d{2}))?$/.exec(nome ?? '');
  if (!partes) return null;
  const minutos = Number(partes[2]) * 60 + Number(partes[3] ?? 0);
  return partes[1] === '-' ? -minutos : minutos;
}

/** Exibe o deslocamento válido para a data local, mantendo o identificador IANA nos cálculos. */
export function formatarFuso(fuso: string, referencia?: string | Date): string {
  try {
    let instante = referencia instanceof Date ? referencia : new Date();
    if (typeof referencia === 'string') {
      const local = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?)?$/.exec(referencia);
      if (local) {
        const hora = Number(local[4] ?? 12);
        const minuto = Number(local[5] ?? 0);
        const paredeUtc = Date.UTC(Number(local[1]), Number(local[2]) - 1,
          Number(local[3]), hora, minuto, Number(local[6] ?? 0));
        instante = new Date(paredeUtc);
        for (let tentativa = 0; tentativa < 3; tentativa++) {
          const deslocamento = deslocamentoMinutos(fuso, instante);
          if (deslocamento === null) return fuso;
          const candidato = new Date(paredeUtc - deslocamento * 60_000);
          if (candidato.getTime() === instante.getTime()) break;
          instante = candidato;
        }
      } else {
        instante = new Date(referencia);
      }
    }
    if (Number.isNaN(instante.getTime())) return fuso;
    const minutos = deslocamentoMinutos(fuso, instante);
    if (minutos === null) return fuso;
    const sinal = minutos < 0 ? '-' : '+';
    const absolutos = Math.abs(minutos);
    const horas = Math.floor(absolutos / 60);
    const resto = absolutos % 60;
    return `UTC${sinal}${horas}${resto ? `:${String(resto).padStart(2, '0')}` : ''}`;
  } catch { return fuso; }
}
