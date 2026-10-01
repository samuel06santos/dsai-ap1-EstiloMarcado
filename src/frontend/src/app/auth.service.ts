import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, map, Observable, of, switchMap, tap } from 'rxjs';

export type Perfil = 'CLIENTE' | 'PROFISSIONAL' | 'RECEPCAO' | 'ADMINISTRADOR';
export type Estado = 'PENDENTE' | 'ATIVA' | 'BLOQUEADA' | 'DESATIVADA';

export interface Sessao {
  id: number;
  nome: string;
  email: string;
  perfil: Perfil;
  unidadeId: number | null;
  profissionalId: number | null;
}

export interface Usuario extends Sessao {
  estado: Estado;
}

export interface MeuPerfil extends Usuario {
  telefoneContato: string | null;
  filial: { id: number; nome: string; ativa: boolean } | null;
  estabelecimento: { id: number; nome: string } | null;
}

export interface ErroApi {
  mensagem?: string;
  campos?: Record<string, string>;
}

export const credentialsInterceptor: HttpInterceptorFn = (request, next) =>
  next(request.clone({ withCredentials: true }));

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  readonly sessao = signal<Sessao | null | undefined>(undefined);
  readonly autenticado = computed(() => this.sessao() !== null && this.sessao() !== undefined);
  readonly administrador = computed(() => this.sessao()?.perfil === 'ADMINISTRADOR');

  carregarSessao(): Observable<Sessao | null> {
    return this.http.get<Sessao>('/api/autenticacao/sessao').pipe(
      tap((sessao) => this.sessao.set(sessao)),
      map((sessao) => sessao as Sessao | null),
      catchError(() => {
        this.sessao.set(null);
        return of(null);
      })
    );
  }

  cadastrar(dados: { nome: string; email: string; senha: string; confirmacaoSenha: string }): Observable<unknown> {
    return this.mutacao(() => this.http.post('/api/autenticacao/cadastros', dados));
  }

  ativar(token: string): Observable<unknown> {
    return this.mutacao(() => this.http.post('/api/autenticacao/ativacoes', { token }));
  }

  login(email: string, senha: string): Observable<Sessao> {
    return this.mutacao(() => this.http.post<Sessao>('/api/autenticacao/sessoes', { email, senha })).pipe(
      tap((sessao) => this.sessao.set(sessao))
    );
  }

  logout(): Observable<unknown> {
    return this.mutacao(() => this.http.delete('/api/autenticacao/sessao')).pipe(
      tap(() => this.sessao.set(null))
    );
  }

  solicitarRecuperacao(email: string): Observable<unknown> {
    return this.mutacao(() => this.http.post('/api/autenticacao/recuperacoes', { email }));
  }

  redefinir(token: string, senha: string, confirmacaoSenha: string): Observable<unknown> {
    return this.mutacao(() =>
      this.http.post('/api/autenticacao/redefinicoes', { token, senha, confirmacaoSenha })
    );
  }

  aceitarConvite(token: string, senha: string, confirmacaoSenha: string): Observable<unknown> {
    return this.mutacao(() =>
      this.http.post('/api/autenticacao/convites', { token, senha, confirmacaoSenha })
    );
  }

  meuPerfil(): Observable<MeuPerfil> {
    return this.http.get<MeuPerfil>('/api/usuarios/me');
  }

  atualizarPerfil(nome: string, telefoneContato: string | null): Observable<MeuPerfil> {
    return this.mutacao(() => this.http.patch<MeuPerfil>('/api/usuarios/me',
      { nome, telefoneContato })).pipe(tap(perfil => {
        const sessao = this.sessao();
        if (sessao) { this.sessao.set({ ...sessao, nome: perfil.nome }); }
      }));
  }

  listarUsuarios(unidadeId: number): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(`/api/unidades/${unidadeId}/usuarios-internos`);
  }

  criarUsuario(unidadeId: number, dados: {
    nome: string;
    email: string;
    perfil: Perfil;
    profissionalId: number | null;
  }): Observable<Usuario> {
    return this.mutacao(() =>
      this.http.post<Usuario>(`/api/unidades/${unidadeId}/usuarios-internos`, dados)
    );
  }

  static mensagemErro(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ErroApi | undefined;
      return body?.mensagem ?? 'Nao foi possivel concluir a operacao.';
    }
    return 'Nao foi possivel concluir a operacao.';
  }

  private mutacao<T>(acao: () => Observable<T>): Observable<T> {
    return this.http.get('/api/autenticacao/csrf').pipe(switchMap(() => acao()));
  }
}
