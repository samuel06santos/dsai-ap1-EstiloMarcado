import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface AgendaItem {
  id: number;
  inicio: string;
  fim: string;
  servico: string;
  cliente: string;
  status: string;
}

@Injectable({ providedIn: 'root' })
export class AgendaProfissionalApi {
  private readonly http = inject(HttpClient);

  agendaDoDia(data: string): Observable<AgendaItem[]> {
    return this.http.get<AgendaItem[]>('/api/painel/agenda', { params: { data } });
  }

  agendaDoIntervalo(de: string, ate: string): Observable<AgendaItem[]> {
    return this.http.get<AgendaItem[]>('/api/painel/agenda', { params: { de, ate } });
  }
}
