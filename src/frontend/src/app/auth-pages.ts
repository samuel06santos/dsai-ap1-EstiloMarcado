import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService, Perfil, Usuario } from './auth.service';

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="hero">
      <div>
        <p class="eyebrow">Seu tempo, bem cuidado</p>
        <h1>Agende beleza sem complicacao.</h1>
        <p class="lead">Encontre servicos e horarios. O estabelecimento cuida do resto.</p>
        <div class="actions">
          <a class="button primary" routerLink="/cadastro">Criar minha conta</a>
          <a class="button ghost" routerLink="/entrar">Ja tenho uma conta</a>
        </div>
      </div>
      <div class="hero-card" aria-hidden="true">
        <span>Proximo horario</span><strong>Hoje, 15:30</strong><small>Corte de cabelo · 30 min</small>
      </div>
    </section>
  `
})
export class HomeComponent {}

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
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
    </section>
  `
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  email = '';
  senha = '';
  readonly erro = signal('');
  readonly enviando = signal(false);

  enviar(): void {
    this.erro.set(''); this.enviando.set(true);
    this.auth.login(this.email, this.senha).pipe(finalize(() => this.enviando.set(false)))
      .subscribe({ next: () => void this.router.navigateByUrl('/conta'), error: (e) => this.erro.set(AuthService.mensagemErro(e)) });
  }
}

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="auth-card wide">
      <p class="eyebrow">Comece agora</p><h1>Crie sua conta</h1>
      @if (sucesso()) {
        <div class="notice success"><strong>Confira seu e-mail.</strong><br>Enviamos as instrucoes de ativacao caso o endereco esteja disponivel.</div>
        <a class="button ghost" routerLink="/entrar">Voltar para o login</a>
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
    this.auth.ativar(token).subscribe({
      next: () => this.mensagem.set('Conta ativada. Agora voce pode entrar.'),
      error: (e) => { this.erro.set(true); this.mensagem.set(AuthService.mensagemErro(e)); }
    });
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
    const token = this.route.snapshot.queryParamMap.get('token') ?? '';
    const acao = this.convite ? this.auth.aceitarConvite(token, this.senha, this.confirmacao) : this.auth.redefinir(token, this.senha, this.confirmacao);
    acao.subscribe({ next: () => this.sucesso.set(true), error: (e) => this.erro.set(AuthService.mensagemErro(e)) });
  }
}

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="content-card"><p class="eyebrow">Minha conta</p><h1>Dados pessoais</h1>
      @if (usuario(); as u) { <form (ngSubmit)="salvar()"><label>Nome<input name="nome" [(ngModel)]="nome" required minlength="2"></label>
        <label>E-mail<input [value]="u.email" disabled></label><p><span class="badge">{{ u.perfil }}</span></p>
        @if (mensagem()) { <p class="notice success">{{ mensagem() }}</p> }<button class="button primary">Salvar nome</button></form> }
    </section>`
})
export class ContaComponent implements OnInit {
  private readonly auth = inject(AuthService); readonly usuario = signal<Usuario | null>(null); readonly mensagem = signal(''); nome = '';
  ngOnInit(): void { this.auth.meuPerfil().subscribe((u) => { this.usuario.set(u); this.nome = u.nome; }); }
  salvar(): void { this.auth.atualizarNome(this.nome).subscribe((u) => { this.usuario.set(u); this.mensagem.set('Dados atualizados.'); }); }
}

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="content-card wide"><div class="section-heading"><div><p class="eyebrow">Administracao</p><h1>Contas da equipe</h1></div></div>
      <form class="inline-form" (ngSubmit)="criar()" #form="ngForm">
        <label>Nome<input name="nome" [(ngModel)]="nome" required></label><label>E-mail<input type="email" name="email" [(ngModel)]="email" required email></label>
        <label>Perfil<select name="perfil" [(ngModel)]="perfil"><option value="PROFISSIONAL">Profissional</option><option value="RECEPCAO">Recepcao</option><option value="ADMINISTRADOR">Administrador</option></select></label>
        @if (perfil === 'PROFISSIONAL') { <label>ID do profissional<input type="number" name="profissional" [(ngModel)]="profissionalId" required></label> }
        <button class="button primary" [disabled]="form.invalid">Enviar convite</button>
      </form>
      @if (erro()) { <p class="notice error">{{ erro() }}</p> }
      <div class="user-list">@for (u of usuarios(); track u.id) { <article><div><strong>{{ u.nome }}</strong><small>{{ u.email }}</small></div><span class="badge">{{ u.perfil }}</span><span class="status">{{ u.estado }}</span></article> }</div>
    </section>`
})
export class UsuariosAdminComponent implements OnInit {
  private readonly auth = inject(AuthService); readonly usuarios = signal<Usuario[]>([]); readonly erro = signal('');
  nome = ''; email = ''; perfil: Perfil = 'PROFISSIONAL'; profissionalId: number | null = null;
  ngOnInit(): void { this.carregar(); }
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
}
