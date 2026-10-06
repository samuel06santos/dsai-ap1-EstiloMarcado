import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, retry } from 'rxjs';
import { AuthService, ErroApi, MeuPerfil, MetodosLogin, Perfil, Usuario } from './auth.service';
import { UiIconComponent } from './ui-icon.component';
import { EstabelecimentoService, Profissional } from './estabelecimento.service';


@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, UiIconComponent],
  template: `
    <section class="auth-card">
      <p class="eyebrow">Acesse sua agenda</p><h1>Entrar</h1>
      <form (ngSubmit)="enviar()" #form="ngForm">
        <label>E-mail<input type="email" name="email" [(ngModel)]="email" required email autocomplete="email"></label>
        <label>Senha<input type="password" name="senha" [(ngModel)]="senha" required autocomplete="current-password"></label>
        @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
        <button class="button primary" [disabled]="form.invalid || enviando()">{{ enviando() ? 'Entrando…' : 'Entrar' }}</button>
      </form>
      <div class="auth-links"><a routerLink="/recuperar-conta">Esqueci minha senha</a><a routerLink="/cadastro">Criar conta</a></div>
      @if (carregandoMetodos()) { <p role="status">Carregando opções de login…</p> }
      @if (erroMetodos()) {
        <div class="notice error" role="alert">
          <p>Não foi possível carregar os métodos de login.</p>
          <button class="button ghost" type="button" (click)="carregarMetodos()">Tentar novamente</button>
        </div>
      }
      @if (firebaseAtivo()) {
        <div style="margin: 2rem 0; border-top: 1px solid #ccc; text-align: center; display: block;"><span style="top: -0.8em; position: relative; background-color: #fff; padding: 0 0.3em;">ou</span></div>
        <div style="display: grid; gap: 0.5rem; margin-top: 1rem; text-align: center;">
          <button class="button ghost" type="button" [disabled]="enviando()" (click)="entrarGoogle()">
            <span><app-icon name="google" /></span>
            {{ enviando() ? 'Aguarde…' : 'Continuar com Google' }}
          </button>
        </div>
      }
    </section>
  `
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  email = '';
  senha = '';
  readonly erro = signal('');
  readonly enviando = signal(false);
  readonly firebaseAtivo = signal(false);
  readonly carregandoMetodos = signal(false);
  readonly erroMetodos = signal(false);

  ngOnInit(): void {
    this.carregarMetodos();
  }

  carregarMetodos(): void {
    if (this.carregandoMetodos()) return;
    this.erroMetodos.set(false);
    this.carregandoMetodos.set(true);
    this.auth.configuracaoFirebase().pipe(
      retry({ count: 15, delay: 4000 }),
      finalize(() => this.carregandoMetodos.set(false))
    ).subscribe({
      next: config => this.firebaseAtivo.set(config.enabled),
      error: () => this.erroMetodos.set(true)
    });
  }

  enviar(): void {
    this.erro.set(''); this.enviando.set(true);
    this.auth.login(this.email, this.senha).pipe(finalize(() => this.enviando.set(false)))
      .subscribe({ next: (sessao) => this.redirecionar(sessao),
        error: (e) => this.erro.set(AuthService.mensagemErro(e)) });
  }

  entrarGoogle(): void {
    if (this.enviando()) return;
    this.erro.set(''); this.enviando.set(true);
    this.auth.loginGoogle().pipe(finalize(() => this.enviando.set(false)))
      .subscribe({ next: sessao => this.redirecionar(sessao),
        error: e => this.erro.set(AuthService.mensagemErro(e)) });
  }

  private redirecionar(sessao: { perfil: Perfil }): void {
        const destino = sessao.perfil === 'PROFISSIONAL' ? '/profissional/agenda'
          : sessao.perfil === 'ADMINISTRADOR' ? '/administracao/estabelecimento'
          : sessao.perfil === 'RECEPCAO' ? '/equipe/agendamentos' : '/';
        const retorno = this.route.snapshot.queryParamMap.get('retorno');
        const seguro = sessao.perfil === 'CLIENTE' && retorno?.startsWith('/unidades/')
          && !retorno.startsWith('//') && !retorno.includes('://');
        void this.router.navigateByUrl(seguro && retorno ? retorno : destino);
  }
}

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="auth-card wide">
      <p class="eyebrow">Comece agora</p><h1>Crie sua conta</h1>
      @if (sucesso()) {
        <div style="display: grid; gap: 1.25rem; margin-top: 1rem;">
          <div class="notice success"><strong>Confira seu e-mail.</strong><br>Enviamos as instrucoes de ativacao caso o endereco esteja disponivel.</div>
          <a class="button ghost" routerLink="/entrar">Voltar para o login</a>
        </div>
      } @else {
        <form (ngSubmit)="enviar()" #form="ngForm">
          <label>Nome<input name="nome" [(ngModel)]="nome" required minlength="2" maxlength="120" autocomplete="name"></label>
          <label>E-mail<input type="email" name="email" [(ngModel)]="email" required email autocomplete="email"></label>
          <div class="form-grid">
            <label>Senha<input type="password" name="senha" [(ngModel)]="senha" required minlength="8" maxlength="72" autocomplete="new-password"></label>
            <label>Confirmar senha<input type="password" name="confirmacao" [(ngModel)]="confirmacaoSenha" required autocomplete="new-password"></label>
          </div>
          <small>Use de 8 a 72 caracteres, com ao menos uma letra e um numero.</small>
          @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
          <button class="button primary" [disabled]="form.invalid || enviando()">Criar conta</button>
        </form>
      }
    </section>
  `
})
export class CadastroComponent {
  private readonly auth = inject(AuthService);
  nome = ''; email = ''; senha = ''; confirmacaoSenha = '';
  readonly erro = signal(''); readonly sucesso = signal(false); readonly enviando = signal(false);
  enviar(): void {
    this.erro.set('');
    if (this.senha !== this.confirmacaoSenha) { this.erro.set('A confirmacao da senha nao confere.'); return; }
    this.enviando.set(true);
    this.auth.cadastrar({ nome: this.nome, email: this.email, senha: this.senha, confirmacaoSenha: this.confirmacaoSenha })
      .pipe(finalize(() => this.enviando.set(false)))
      .subscribe({ next: () => this.sucesso.set(true), error: (e) => this.erro.set(AuthService.mensagemErro(e)) });
  }
}

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `<section class="auth-card"><h1>Ativacao da conta</h1><p class="notice" [class.error]="erro()">{{ mensagem() }}</p><a class="button primary" routerLink="/entrar">Ir para o login</a></section>`
})
export class AtivacaoComponent implements OnInit {
  private readonly auth = inject(AuthService); private readonly route = inject(ActivatedRoute);
  readonly mensagem = signal('Validando seu link…'); readonly erro = signal(false);
  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token') ?? '';
    const code = this.route.snapshot.queryParamMap.get('oobCode');
    this.auth.configuracaoFirebase().subscribe({
      next: config => {
        if (config.enabled && !code) {
          this.mensagem.set('Confirme o e-mail pelo link recebido e depois entre na sua conta.');
          return;
        }
        (config.enabled ? this.auth.confirmarEmailFirebase(code!) : this.auth.ativar(token)).subscribe({
          next: () => this.mensagem.set('E-mail confirmado. Agora você pode entrar.'),
          error: e => { this.erro.set(true); this.mensagem.set(AuthService.mensagemErro(e)); }
        });
      },
      error: () => { this.erro.set(true); this.mensagem.set('Não foi possível verificar o link.'); }
    });
  }
}

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `<section class="auth-card"><h1>Ação de e-mail</h1>
    <p class="notice" role="status">{{ mensagem() }}</p>
    <a routerLink="/entrar">Voltar ao login</a></section>`
})
export class FirebaseEmailActionComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly mensagem = signal('Validando o link…');

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('oobCode');
    const mode = this.route.snapshot.queryParamMap.get('mode');
    if (!code || !['verifyEmail', 'resetPassword'].includes(mode ?? '')) {
      this.mensagem.set('Este link não é válido. Solicite um novo.');
      return;
    }
    const destino = mode === 'verifyEmail' ? '/ativar' : '/redefinir-senha';
    void this.router.navigate([destino], { queryParams: { oobCode: code }, replaceUrl: true });
  }
}

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="auth-card"><p class="eyebrow">Recuperacao</p><h1>Recupere sua conta</h1>
      @if (sucesso()) { <p class="notice success">Se existir uma conta apta, enviaremos as instrucoes por e-mail.</p> }
      @else { <form (ngSubmit)="enviar()" #form="ngForm"><label>E-mail<input type="email" name="email" [(ngModel)]="email" required email></label>
        @if (erro()) { <p class="notice error">{{ erro() }}</p> }<button class="button primary" [disabled]="form.invalid">Enviar instrucoes</button></form> }
      <a routerLink="/entrar">Voltar para o login</a>
    </section>`
})
export class RecuperacaoComponent {
  private readonly auth = inject(AuthService); email = ''; readonly sucesso = signal(false); readonly erro = signal('');
  enviar(): void { this.auth.solicitarRecuperacao(this.email).subscribe({ next: () => this.sucesso.set(true), error: (e) => this.erro.set(AuthService.mensagemErro(e)) }); }
}

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="auth-card"><p class="eyebrow">{{ convite ? 'Convite' : 'Nova senha' }}</p><h1>{{ convite ? 'Conclua seu acesso' : 'Defina uma nova senha' }}</h1>
      @if (sucesso()) { <p class="notice success">Senha definida com sucesso. Entre com suas credenciais.</p><a class="button primary" routerLink="/entrar">Entrar</a> }
      @else { <form (ngSubmit)="enviar()" #form="ngForm"><label>Nova senha<input type="password" name="senha" [(ngModel)]="senha" required minlength="8" maxlength="72"></label>
        <label>Confirmar senha<input type="password" name="confirmacao" [(ngModel)]="confirmacao" required></label>
        @if (erro()) { <p class="notice error">{{ erro() }}</p> }<button class="button primary" [disabled]="form.invalid">Salvar senha</button></form> }
    </section>`
})
export class NovaSenhaComponent {
  private readonly auth = inject(AuthService); private readonly route = inject(ActivatedRoute);
  readonly convite = this.route.snapshot.data['convite'] === true;
  senha = ''; confirmacao = ''; readonly sucesso = signal(false); readonly erro = signal('');
  enviar(): void {
    if (this.senha !== this.confirmacao) { this.erro.set('A confirmacao da senha nao confere.'); return; }
    const token = this.route.snapshot.queryParamMap.get(this.convite ? 'token' : 'oobCode')
      ?? this.route.snapshot.queryParamMap.get('token') ?? '';
    const acao = this.convite ? this.auth.aceitarConvite(token, this.senha, this.confirmacao) : this.auth.redefinir(token, this.senha, this.confirmacao);
    acao.subscribe({ next: () => this.sucesso.set(true), error: (e) => this.erro.set(AuthService.mensagemErro(e)) });
  }
}

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, UiIconComponent],
  template: `
    <section class="profile-page page-stack">
      <div class="page-heading"><p class="eyebrow">Minha conta</p><h1>Meu perfil</h1>
        <p>Cuide dos seus dados e mantenha seu contato atualizado.</p></div>
      @if (usuario(); as u) {
        <div class="profile-grid">
          <div class="profile-identity surface-panel">
            <div class="profile-avatar"><img src="/avatar.svg" width="96" height="96" alt="Avatar padrão"></div>
            <h2>{{ u.nome }}</h2><span class="badge">{{ tituloPerfil(u.perfil) }}</span>
            <p>A troca de foto estará disponível futuramente.</p>
          </div>
          <div class="surface-panel profile-details">
            <div class="section-title"><div><p class="eyebrow">Dados pessoais</p><h2>Informações de contato</h2></div><app-icon name="user" /></div>
            <form (ngSubmit)="salvar()" #form="ngForm">
              <label>Nome completo<input name="nome" [(ngModel)]="nome" required minlength="2" maxlength="120" autocomplete="name"></label>
              <label>E-mail<input [value]="u.email" disabled aria-describedby="email-ajuda"></label>
              <p id="email-ajuda" class="field-help">Seu e-mail é somente leitura e não pode ser alterado aqui.</p>
              <label>Telefone / WhatsApp<input type="tel" name="telefoneContato" [(ngModel)]="telefone"
                inputmode="tel" autocomplete="tel" maxlength="30" placeholder="(91) 99999-9999"></label>
              <p class="field-help">Opcional. Informe o DDD ou use o formato internacional com +.</p>
              @if (errosCampos()['telefoneContato']) { <p class="field-error" role="alert">{{ errosCampos()['telefoneContato'] }}</p> }
              @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
              @if (mensagem()) { <p class="notice success" role="status">{{ mensagem() }}</p> }
              <div class="form-actions"><button class="button primary" type="submit"
                [disabled]="form.invalid || !telefoneValido() || !alterado() || salvando()">
                <app-icon name="check" /> {{ salvando() ? 'Salvando…' : 'Salvar alterações' }}
              </button></div>
            </form>
          </div>
        </div>
        @if (metodos(); as m) {
          @if (m.firebaseUid) {
            <div class="surface-panel section-card">
              <div class="section-title"><div><p class="eyebrow">Acesso</p><h2>Formas de entrar</h2></div></div>
              <p>E-mail e senha: {{ m.emailSenha ? 'Disponível' : 'Não configurado' }}</p>
              <p>Google: {{ m.google ? 'Vinculado' : 'Não vinculado' }}</p>
              @if (!m.google && m.emailSenha) {
                <label>Confirme sua senha para vincular o Google
                  <input type="password" [(ngModel)]="senhaVinculo" autocomplete="current-password">
                </label>
                <button class="button ghost" type="button" [disabled]="!senhaVinculo || vinculando()"
                  (click)="vincularGoogle()">{{ vinculando() ? 'Vinculando…' : 'Vincular Google' }}</button>
              }
            </div>
          }
        }
        @if (u.filial && u.estabelecimento) {
          <div class="surface-panel affiliation-panel">
            <div class="section-title"><div><p class="eyebrow">Seu local de trabalho</p><h2>Meu vínculo</h2></div><app-icon name="building" /></div>
            <div class="affiliation-grid">
              <div><span>Filial</span><a [routerLink]="u.perfil === 'ADMINISTRADOR' ? '/administracao/estabelecimento' : '/minha-filial'"
                fragment="filial">{{ u.filial.nome }} <app-icon name="arrow" /></a>
                <small>{{ u.filial.ativa ? 'Ativa' : 'Inativa' }}</small></div>
              <div><span>Estabelecimento</span><a [routerLink]="u.perfil === 'ADMINISTRADOR' ? '/administracao/estabelecimento' : '/minha-filial'"
                fragment="estabelecimento">{{ u.estabelecimento.nome }} <app-icon name="arrow" /></a></div>
            </div>
          </div>
        }
      } @else if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
    </section>`
})
export class ContaComponent implements OnInit {
  private readonly auth = inject(AuthService);
  readonly usuario = signal<MeuPerfil | null>(null);
  readonly mensagem = signal('');
  readonly erro = signal('');
  readonly errosCampos = signal<Record<string, string>>({});
  readonly salvando = signal(false);
  readonly metodos = signal<MetodosLogin | null>(null);
  readonly vinculando = signal(false);
  nome = '';
  telefone = '';
  senhaVinculo = '';
  ngOnInit(): void {
    this.auth.meuPerfil().subscribe({
      next: u => { this.usuario.set(u); this.nome = u.nome; this.telefone = u.telefoneContato ?? ''; },
      error: e => this.erro.set(AuthService.mensagemErro(e))
    });
    this.auth.metodosLogin().subscribe({ next: m => this.metodos.set(m) });
  }
  vincularGoogle(): void {
    const m = this.metodos(); const u = this.usuario();
    if (!m?.firebaseUid || !u || !this.senhaVinculo || this.vinculando()) return;
    this.erro.set(''); this.mensagem.set(''); this.vinculando.set(true);
    this.auth.vincularGoogle(u.email, this.senhaVinculo, m.firebaseUid)
      .pipe(finalize(() => { this.vinculando.set(false); this.senhaVinculo = ''; }))
      .subscribe({ next: () => {
        this.mensagem.set('Google vinculado à sua conta.');
        this.auth.metodosLogin().subscribe({ next: atual => this.metodos.set(atual) });
      }, error: e => this.erro.set(AuthService.mensagemErro(e)) });
  }
  tituloPerfil(perfil: Perfil): string {
    return { CLIENTE: 'Cliente', PROFISSIONAL: 'Profissional',
      RECEPCAO: 'Recepção', ADMINISTRADOR: 'Administrador' }[perfil];
  }
  alterado(): boolean {
    const atual = this.usuario();
    return !!atual && (this.nome.trim() !== atual.nome ||
      this.telefone.trim() !== (atual.telefoneContato ?? ''));
  }
  telefoneValido(): boolean {
    const texto = this.telefone.trim();
    if (!texto) { return true; }
    if (!/^\+?[0-9() .-]+$/.test(texto)) { return false; }
    const tamanho = texto.replace(/\D/g, '').length;
    return texto.startsWith('+') ? tamanho >= 8 && tamanho <= 15 : tamanho === 10 || tamanho === 11;
  }
  salvar(): void {
    if (!this.alterado() || !this.telefoneValido() || this.salvando()) { return; }
    this.erro.set(''); this.errosCampos.set({}); this.mensagem.set(''); this.salvando.set(true);
    this.auth.atualizarPerfil(this.nome, this.telefone.trim() || null)
      .pipe(finalize(() => this.salvando.set(false))).subscribe({
        next: u => { this.usuario.set(u); this.nome = u.nome; this.telefone = u.telefoneContato ?? '';
          this.mensagem.set('Dados atualizados com sucesso.'); },
        error: e => {
          this.erro.set(AuthService.mensagemErro(e));
          if (e instanceof HttpErrorResponse) {
            this.errosCampos.set((e.error as ErroApi | undefined)?.campos ?? {});
          }
        }
      });
  }
}

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, UiIconComponent],
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Administração</p><h1>Contas da equipe</h1>
        <p>Convide pessoas para trabalhar na sua filial e acompanhe seus acessos.</p></div>
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      <section class="surface-panel section-card">
        <div class="section-title"><div><p class="eyebrow">Convites</p><h2>Adicionar alguém à equipe</h2></div><app-icon name="users" /></div>
        <form (ngSubmit)="criar()" #form="ngForm">
          <div class="form-grid">
            <label>Nome<input name="nome" [(ngModel)]="nome" required minlength="2" maxlength="120"></label>
            <label>E-mail<input type="email" name="email" [(ngModel)]="email" required email></label>
            <label>Perfil<select name="perfil" [(ngModel)]="perfil">
              <option value="PROFISSIONAL">Profissional</option><option value="RECEPCAO">Recepção</option>
              <option value="ADMINISTRADOR">Administrador</option></select></label>
            @if (perfil === 'PROFISSIONAL') {
              <label>Profissional<select name="profissional" [(ngModel)]="profissionalId" required>
                <option [ngValue]="null">Selecione uma pessoa</option>
                @for (p of profissionais(); track p.id) { <option [ngValue]="p.id">{{ p.nome }}</option> }
              </select></label>
            }
          </div>
          <div class="form-actions"><button class="button primary" [disabled]="form.invalid">Enviar convite</button></div>
        </form>
      </section>
      <section class="surface-panel section-card">
        <div class="section-title"><div><p class="eyebrow">Pessoas</p><h2>Equipe da filial</h2></div><app-icon name="users" /></div>
        <div class="user-list">@for (u of usuarios(); track u.id) {
          <article><div><strong>{{ u.nome }}</strong><small>{{ u.email }}</small></div>
            <span class="badge">{{ u.perfil }}</span><span class="status">{{ u.estado }}</span></article>
        } @empty { <p class="muted-copy">Ainda não há contas internas nesta filial.</p> }</div>
      </section>
    </section>`
})
export class UsuariosAdminComponent implements OnInit {
  private readonly auth = inject(AuthService); readonly usuarios = signal<Usuario[]>([]); readonly erro = signal('');
  private readonly estabelecimento = inject(EstabelecimentoService);
  readonly profissionais = signal<Profissional[]>([]);
  nome = ''; email = ''; perfil: Perfil = 'PROFISSIONAL'; profissionalId: number | null = null;
  ngOnInit(): void { this.carregar(); this.carregarProfissionais(); }
  criar(): void {
    const unidade = this.auth.sessao()?.unidadeId;
    if (unidade === null || unidade === undefined) { return; }
    this.auth.criarUsuario(unidade, { nome: this.nome, email: this.email, perfil: this.perfil, profissionalId: this.profissionalId })
      .subscribe({ next: () => { this.nome = ''; this.email = ''; this.profissionalId = null; this.carregar(); }, error: (e) => this.erro.set(AuthService.mensagemErro(e)) });
  }
  private carregar(): void {
    const unidade = this.auth.sessao()?.unidadeId;
    if (unidade !== null && unidade !== undefined) { this.auth.listarUsuarios(unidade).subscribe((u) => this.usuarios.set(u)); }
  }
  private carregarProfissionais(): void {
    const unidade = this.auth.sessao()?.unidadeId;
    if (unidade != null) {
      this.estabelecimento.profissionais(unidade, true).subscribe({
        next: lista => this.profissionais.set(lista.filter(p => p.ativo)),
        error: e => this.erro.set(AuthService.mensagemErro(e))
      });
    }
  }
}
