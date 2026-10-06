import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ServicoDescoberta {
  codigo: string;
  nome: string;
  precoMinimo: number;
}

export interface FilialDescoberta {
  id: number;
  nome: string;
  estabelecimento: string;
  endereco: string | null;
  precoMinimo: number | null;
  precoMaximo: number | null;
  servicos: ServicoDescoberta[];
}

export interface PaginaFiliais {
  itens: FilialDescoberta[];
  total: number;
  pagina: number;
  tamanho: number;
  totalPaginas: number;
}

export interface ServicoOpcao {
  codigo: string;
  nome: string;
}

export interface FiltrosFiliais {
  busca: string;
  servicos: string[];
  pagina: number;
}

@Injectable({ providedIn: 'root' })
export class FilialDescobertaService {
  private readonly http = inject(HttpClient);

  listar(filtros: FiltrosFiliais): Observable<PaginaFiliais> {
    let params = new HttpParams().set('pagina', filtros.pagina).set('tamanho', 12);
    if (filtros.busca) { params = params.set('busca', filtros.busca); }
    for (const servico of filtros.servicos) { params = params.append('servico', servico); }
    return this.http.get<PaginaFiliais>('/api/filiais/publicas', { params });
  }

  servicos(): Observable<ServicoOpcao[]> {
    return this.http.get<ServicoOpcao[]>('/api/filiais/publicas/servicos');
  }
}
