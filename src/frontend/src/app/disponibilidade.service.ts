import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

export interface JornadaIntervalo {
  id?: number;
  diaSemana: number;
  horaInicio: string;
  horaFim: string;
}

export interface Jornada {
  profissionalId: number;
  unidadeId: number;
  intervalos: JornadaIntervalo[];
}

export interface Janela {
  inicio: string;
  fim: string;
}

export interface Horario {
  id?: number;
  horaInicio: string;
  horaFim: string;
}

export interface ExcecaoJornada {
  id: number;
  profissionalId: number;
  data: string;
  tipo: 'FOLGA' | 'JORNADA_ESPECIAL';
  motivo: string | null;
  intervalos: Horario[];
}

export interface Afastamento {
  id: number;
  profissionalId: number;
  dataInicio: string;
  dataFim: string;
  tipo: string;
  descricao: string | null;
}

export interface Feriado {
  id: number;
  unidadeId: number;
  data: string;
  nome: string;
}

export interface Bloqueio {
  id: number;
  unidadeId: number;
  profissionalId: number | null;
  data: string;
  diaInteiro: boolean;
  horaInicio: string | null;
  horaFim: string | null;
  motivo: string | null;
}

export interface Conflito {
  id: number;
  inicio: string;
  servicoId: number;
  clienteId: number;
}

@Injectable({ providedIn: 'root' })
export class DisponibilidadeService {
  private readonly http = inject(HttpClient);

  // ----- Administrativo e leitura por filial/profissional -----

  jornada(unidadeId: number, profissionalId: number): Observable<Jornada> {
    return this.http.get<Jornada>(
      `/api/unidades/${unidadeId}/profissionais/${profissionalId}/jornada`);
  }

  atualizarJornada(unidadeId: number, profissionalId: number,
                   intervalos: JornadaIntervalo[]): Observable<Jornada> {
    return this.mutar(() => this.http.put<Jornada>(
      `/api/unidades/${unidadeId}/profissionais/${profissionalId}/jornada`, { intervalos }));
  }

  janelas(unidadeId: number, profissionalId: number, data: string): Observable<Janela[]> {
    return this.http.get<Janela[]>(
      `/api/unidades/${unidadeId}/profissionais/${profissionalId}/janelas`, { params: { data } });
  }

  // ----- Autoatendimento do profissional -----

  minhaJornada(): Observable<Jornada> {
    return this.http.get<Jornada>('/api/profissionais/me/jornada');
  }

  atualizarMinhaJornada(intervalos: JornadaIntervalo[]): Observable<Jornada> {
    return this.mutar(() => this.http.put<Jornada>('/api/profissionais/me/jornada', { intervalos }));
  }

  minhasJanelas(data: string): Observable<Janela[]> {
    return this.http.get<Janela[]>('/api/profissionais/me/janelas', { params: { data } });
  }

  minhasExcecoes(de: string, ate: string): Observable<ExcecaoJornada[]> {
    return this.http.get<ExcecaoJornada[]>('/api/profissionais/me/excecoes', { params: { de, ate } });
  }

  criarMinhaExcecao(corpo: { data: string; tipo: string; motivo?: string; intervalos?: Horario[] })
      : Observable<ExcecaoJornada> {
    return this.mutar(() => this.http.post<ExcecaoJornada>('/api/profissionais/me/excecoes', corpo));
  }

  removerMinhaExcecao(id: number): Observable<unknown> {
    return this.mutar(() => this.http.delete(`/api/profissionais/me/excecoes/${id}`));
  }

  meusAfastamentos(de: string, ate: string): Observable<Afastamento[]> {
    return this.http.get<Afastamento[]>('/api/profissionais/me/afastamentos', { params: { de, ate } });
  }

  criarMeuAfastamento(corpo: { dataInicio: string; dataFim: string; tipo: string; descricao?: string })
      : Observable<Afastamento> {
    return this.mutar(() => this.http.post<Afastamento>('/api/profissionais/me/afastamentos', corpo));
  }

  removerMeuAfastamento(id: number): Observable<unknown> {
    return this.mutar(() => this.http.delete(`/api/profissionais/me/afastamentos/${id}`));
  }

  criarMeuBloqueio(corpo: {
    data: string; diaInteiro: boolean; horaInicio?: string | null;
    horaFim?: string | null; motivo?: string;
  }): Observable<Bloqueio> {
    return this.mutar(() => this.http.post<Bloqueio>('/api/profissionais/me/bloqueios', corpo));
  }

  removerMeuBloqueio(id: number): Observable<unknown> {
    return this.mutar(() => this.http.delete(`/api/profissionais/me/bloqueios/${id}`));
  }

  // ----- Feriados e bloqueios de filial (administracao) -----

  feriados(unidadeId: number, de: string, ate: string): Observable<Feriado[]> {
    return this.http.get<Feriado[]>(`/api/unidades/${unidadeId}/feriados`, { params: { de, ate } });
  }

  criarFeriado(unidadeId: number, data: string, nome: string): Observable<Feriado> {
    return this.mutar(() => this.http.post<Feriado>(
      `/api/unidades/${unidadeId}/feriados`, { data, nome }));
  }

  removerFeriado(unidadeId: number, id: number): Observable<unknown> {
    return this.mutar(() => this.http.delete(`/api/unidades/${unidadeId}/feriados/${id}`));
  }

  bloqueios(unidadeId: number, de: string, ate: string,
            profissionalId?: number): Observable<Bloqueio[]> {
    const params: Record<string, string> = { de, ate };
    if (profissionalId != null) { params['profissionalId'] = String(profissionalId); }
    return this.http.get<Bloqueio[]>(`/api/unidades/${unidadeId}/bloqueios`, { params });
  }

  criarBloqueio(unidadeId: number, corpo: {
    profissionalId?: number | null; data: string; diaInteiro: boolean;
    horaInicio?: string | null; horaFim?: string | null; motivo?: string;
  }): Observable<Bloqueio> {
    return this.mutar(() => this.http.post<Bloqueio>(`/api/unidades/${unidadeId}/bloqueios`, corpo));
  }

  removerBloqueio(unidadeId: number, id: number): Observable<unknown> {
    return this.mutar(() => this.http.delete(`/api/unidades/${unidadeId}/bloqueios/${id}`));
  }

  private mutar<T>(acao: () => Observable<T>): Observable<T> {
    return this.http.get('/api/autenticacao/csrf').pipe(switchMap(() => acao()));
  }
}
