import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { ConsultaHorarios } from './estabelecimento.service';

export type EstadoAgendamento = 'AGENDADO' | 'CONFIRMADO' | 'CANCELADO';

export interface Agendamento {
  id: number;
  unidadeId: number;
  unidadeNome?: string;
  unidadeAtiva?: boolean;
  clienteId: number | null;
  servicoId: number;
  servicoNome: string;
  servicoAtivo?: boolean;
  precoAcordado: number;
  profissionalId: number;
  profissionalNome?: string;
  inicio: string;
  fim: string;
  fusoHorario: string;
  status: EstadoAgendamento;
  criadoEm: string;
  atualizadoEm: string;
  canceladoEm: string | null;
  motivoCancelamento: string | null;
}

export interface OpcaoCliente {
  id: number;
  nome: string;
}

export interface ResumoPainel {
  proximosAtivos: number;
  realizados: number;
  cancelados: number;
}

export interface PainelCliente {
  agora: string;
  cliente: { nome: string; telefoneContato: string | null };
  proximo: Agendamento | null;
  proximos: Agendamento[];
  historico: Agendamento[];
  resumo: ResumoPainel;
}

@Injectable({ providedIn: 'root' })
export class AgendamentoApi {
  private readonly http = inject(HttpClient);

  criar(unidadeId: number, dados: { servicoId: number; profissionalId: number;
    inicio: string; clienteId?: number; clienteNome?: string; clienteTelefone?: string },
    chave: string): Observable<Agendamento> {
    return this.mutar(() => this.http.post<Agendamento>(`/api/unidades/${unidadeId}/agendamentos`, dados,
      { headers: { 'Idempotency-Key': chave } }));
  }

  meus(de: string, ate: string, filtros: {
    pagina?: number; status?: EstadoAgendamento; unidadeId?: number; servicoId?: number;
  } = {}): Observable<Agendamento[]> {
    const params: Record<string, string> = {
      de, ate, pagina: String(filtros.pagina ?? 0), tamanho: '100'
    };
    if (filtros.status) { params['status'] = filtros.status; }
    if (filtros.unidadeId != null) { params['unidadeId'] = String(filtros.unidadeId); }
    if (filtros.servicoId != null) { params['servicoId'] = String(filtros.servicoId); }
    return this.http.get<Agendamento[]>('/api/me/agendamentos', { params });
  }

  painel(limiteProximos = 5, limiteHistorico = 5): Observable<PainelCliente> {
    return this.http.get<PainelCliente>('/api/me/painel', {
      params: { limiteProximos: String(limiteProximos), limiteHistorico: String(limiteHistorico) }
    });
  }

  filial(unidadeId: number, de: string, ate: string, profissionalId?: number): Observable<Agendamento[]> {
    const params: Record<string, string> = { de, ate, tamanho: '100' };
    if (profissionalId != null) { params['profissionalId'] = String(profissionalId); }
    return this.http.get<Agendamento[]>(`/api/unidades/${unidadeId}/agendamentos`, { params });
  }

  detalhe(id: number): Observable<Agendamento> {
    return this.http.get<Agendamento>(`/api/agendamentos/${id}`);
  }

  clientesDaUnidade(unidadeId: number): Observable<OpcaoCliente[]> {
    return this.http.get<OpcaoCliente[]>(`/api/unidades/${unidadeId}/clientes`);
  }

  horariosReagendamento(id: number, data: string): Observable<ConsultaHorarios> {
    return this.http.get<ConsultaHorarios>(`/api/agendamentos/${id}/horarios-reagendamento`,
      { params: { data } });
  }

  confirmar(id: number): Observable<Agendamento> {
    return this.mutar(() => this.http.post<Agendamento>(`/api/agendamentos/${id}/confirmacoes`, {}));
  }

  cancelar(id: number, motivo: string): Observable<Agendamento> {
    return this.mutar(() => this.http.post<Agendamento>(`/api/agendamentos/${id}/cancelamentos`, { motivo }));
  }

  reagendar(id: number, inicio: string): Observable<Agendamento> {
    return this.mutar(() => this.http.patch<Agendamento>(`/api/agendamentos/${id}/reagendamento`, { inicio }));
  }

  private mutar<T>(acao: () => Observable<T>): Observable<T> {
    return this.http.get('/api/autenticacao/csrf').pipe(switchMap(() => acao()));
  }
}
