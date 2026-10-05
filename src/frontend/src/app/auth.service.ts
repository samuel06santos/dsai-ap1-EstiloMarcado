import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, from, map, Observable, of, switchMap, tap } from 'rxjs';
import { FirebaseClientService, FirebasePublicConfig } from './firebase-client.service';

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

export interface MetodosLogin {
  firebaseUid: string;
  google: boolean;
  emailSenha: boolean;
}

export const credentialsInterceptor: HttpInterceptorFn = (request, next) =>
  next(request.clone({ withCredentials: true }));

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly firebase = inject(FirebaseClientService);
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
    return from(this.firebase.config()).pipe(switchMap(config => config.enabled
      ? from(this.firebase.passwordToken(email, senha)).pipe(
          switchMap(idToken => this.mutacao(() => this.http.post<Sessao>(
            '/api/autenticacao/sessoes/firebase', { idToken }))),
          finalize(() => { void this.firebase.clear(); }))
      : this.mutacao(() => this.http.post<Sessao>('/api/autenticacao/sessoes', { email, senha })))).pipe(
      tap((sessao) => this.sessao.set(sessao))
    );
  }

  loginGoogle(): Observable<Sessao> {
    return from(this.firebase.googleToken()).pipe(
      switchMap(idToken => this.mutacao(() => this.http.post<Sessao>(
        '/api/autenticacao/sessoes/firebase', { idToken }))),
      tap(sessao => this.sessao.set(sessao)),
      finalize(() => { void this.firebase.clear(); })
    );
  }

  configuracaoFirebase(): Observable<FirebasePublicConfig> { return from(this.firebase.config()); }

  confirmarEmailFirebase(code: string): Observable<void> {
    return from(this.firebase.applyVerificationCode(code)).pipe(
      finalize(() => { void this.firebase.clear(); }));
  }

  metodosLogin(): Observable<MetodosLogin> {
    return this.http.get<MetodosLogin>('/api/autenticacao/contas/metodos');
  }

  vincularGoogle(email: string, senha: string, uid: string): Observable<unknown> {
    return from(this.firebase.linkGoogle(email, senha, uid)).pipe(
      switchMap(idToken => this.mutacao(() => this.http.post(
        '/api/autenticacao/contas/google', { idToken }))),
      finalize(() => { void this.firebase.clear(); })
    );
  }

  logout(): Observable<unknown> {
    return this.mutacao(() => this.http.delete('/api/autenticacao/sessao')).pipe(
      tap(() => { this.sessao.set(null); void this.firebase.clear(); })
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
    if ((error as { code?: string })?.code?.startsWith('auth/')) {
      return FirebaseClientService.message(error);
    }
    if (error instanceof Error) return error.message;
    return 'Nao foi possivel concluir a operacao.';
  }

  private mutacao<T>(acao: () => Observable<T>): Observable<T> {
    return this.http.get('/api/autenticacao/csrf').pipe(switchMap(() => acao()));
  }
}
