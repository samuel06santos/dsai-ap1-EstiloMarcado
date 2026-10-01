import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

export interface Filial {
  id: number;
  estabelecimentoId: number;
  nome: string;
  endereco: string | null;
  telefone: string | null;
  fusoHorario: string;
  ativa: boolean;
  principal: boolean;
}

export interface Estabelecimento {
  id: number;
  nome: string;
  administradorPrincipal: boolean;
  filiais: { id: number; nome: string; ativa: boolean }[];
}

export interface Profissional {
  id: number;
  unidadeId: number;
  nome: string;
  apresentacao: string | null;
  ativo: boolean;
}

export interface ServicoPublico {
  id: number;
  nome: string;
  duracaoMinutos: number;
  preco: number;
  profissionais: { id: number; nome: string; ativo: boolean }[];
}

export interface HorarioDisponivel {
  profissionalId: number;
  inicio: string;
  fim: string;
}

export interface ConsultaHorarios {
  unidadeId: number;
  servicoId: number;
  data: string;
  fusoHorario: string;
  horarios: HorarioDisponivel[];
}

export interface FilialDados {
  nome: string;
  endereco: string;
  telefone: string;
  fusoHorario: string;
  ativa?: boolean;
}

@Injectable({ providedIn: 'root' })
export class EstabelecimentoService {
  private readonly http = inject(HttpClient);

  filial(id: number): Observable<Filial> {
    return this.http.get<Filial>(`/api/unidades/${id}`);
  }

  minhaFilial(): Observable<Filial> {
    return this.http.get<Filial>('/api/unidades/me');
  }

  filialPublica(id: number): Observable<Filial> {
    return this.http.get<Filial>(`/api/unidades/${id}/publico`);
  }

  estabelecimento(id: number): Observable<Estabelecimento> {
    return this.http.get<Estabelecimento>(`/api/estabelecimentos/${id}`);
  }

  atualizarEstabelecimento(id: number, nome: string): Observable<Estabelecimento> {
    return this.mutar(() => this.http.patch<Estabelecimento>(`/api/estabelecimentos/${id}`, { nome }));
  }

  criarFilial(id: number, dados: FilialDados, nomeAdmin: string, emailAdmin: string): Observable<Filial> {
    return this.mutar(() => this.http.post<Filial>(`/api/estabelecimentos/${id}/unidades`, {
      ...dados,
      primeiroAdministrador: { nome: nomeAdmin, email: emailAdmin }
    }));
  }

  atualizarFilial(id: number, dados: FilialDados): Observable<Filial> {
    return this.mutar(() => this.http.patch<Filial>(`/api/unidades/${id}`, dados));
  }

  profissionais(id: number, incluirInativos = false): Observable<Profissional[]> {
    return this.http.get<Profissional[]>(`/api/unidades/${id}/profissionais`, {
      params: incluirInativos ? { incluirInativos: 'true' } : {}
    });
  }

  profissional(id: number, profissionalId: number): Observable<Profissional> {
    return this.http.get<Profissional>(`/api/unidades/${id}/profissionais/${profissionalId}`);
  }

  criarProfissional(id: number, nome: string, apresentacao: string): Observable<Profissional> {
    return this.mutar(() => this.http.post<Profissional>(`/api/unidades/${id}/profissionais`,
      { nome, apresentacao }));
  }

  atualizarProfissional(id: number, profissional: Profissional): Observable<Profissional> {
    return this.mutar(() => this.http.patch<Profissional>(
      `/api/unidades/${id}/profissionais/${profissional.id}`,
      { nome: profissional.nome, apresentacao: profissional.apresentacao, ativo: profissional.ativo }
    ));
  }

  servicosDisponiveis(id: number): Observable<ServicoPublico[]> {
    return this.http.get<ServicoPublico[]>(`/api/unidades/${id}/servicos`,
      { params: { somenteDisponiveis: 'true' } });
  }

  horarios(unidadeId: number, servicoId: number, data: string,
           profissionalId?: number): Observable<ConsultaHorarios> {
    const params: Record<string, string> = { data };
    if (profissionalId != null) { params['profissionalId'] = String(profissionalId); }
    return this.http.get<ConsultaHorarios>(
      `/api/unidades/${unidadeId}/servicos/${servicoId}/horarios`, { params });
  }

  private mutar<T>(acao: () => Observable<T>): Observable<T> {
    return this.http.get('/api/autenticacao/csrf').pipe(switchMap(() => acao()));
  }
}
