export interface AvisoReferencia {
  tipo: string;
  referenciaTipo: string;
  referenciaId: number;
}

export function tituloNotificacao(aviso: AvisoReferencia): string {
  return { CRIACAO: 'Agendamento criado', CONFIRMACAO: 'Agendamento confirmado',
    CANCELAMENTO: 'Agendamento cancelado', REAGENDAMENTO: 'Agendamento reagendado',
    OFERTA: 'Vaga disponível' }[aviso.tipo] ?? 'Atualização da sua conta';
}

export function resumoNotificacao(aviso: AvisoReferencia): string {
  if (aviso.referenciaTipo === 'AGENDAMENTO') { return 'Abra os detalhes do atendimento.'; }
  if (aviso.referenciaTipo === 'OFERTA') { return 'Confira a oferta na sua lista de espera.'; }
  return 'Veja as novidades da sua conta.';
}

export function destinoNotificacao(aviso: AvisoReferencia):
    { tipo: 'AGENDAMENTO' | 'OFERTA'; url: string } | null {
  if (!Number.isSafeInteger(aviso.referenciaId) || aviso.referenciaId <= 0) { return null; }
  if (aviso.referenciaTipo === 'AGENDAMENTO') {
    return { tipo: 'AGENDAMENTO', url: `/agendamentos/${aviso.referenciaId}/historico` };
  }
  if (aviso.referenciaTipo === 'OFERTA') {
    return { tipo: 'OFERTA', url: `/lista-espera?ofertaId=${aviso.referenciaId}` };
  }
  return null;
}

export function ordenarNotificacoes<T extends { id: number; criadoEm: string }>(itens: T[]): T[] {
  return [...itens].sort((a, b) =>
    Date.parse(b.criadoEm) - Date.parse(a.criadoEm) || b.id - a.id);
}
