import { ChangeDetectionStrategy, Component, ElementRef, HostListener, ViewChild,
  computed, effect, inject, OnInit, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { filter, finalize } from 'rxjs';
import { AuthService, Perfil } from './auth.service';
import { EstabelecimentoService } from './estabelecimento.service';
import { IconName, UiIconComponent } from './ui-icon.component';

interface NavItem {
  texto: string;
  icone: IconName;
  destino: string;
  fragmento?: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterLink, RouterOutlet, UiIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="app-header">
      <div class="header-leading">
        @if (auth.sessao() && !paginaPublica()) {
          <button class="icon-button mobile-menu" type="button" (click)="alternarSidebar()"
            [attr.aria-label]="sidebarAberta() ? 'Fechar menu' : 'Abrir menu'"
            [attr.aria-expanded]="sidebarAberta()" aria-controls="app-sidebar">
            <app-icon [name]="sidebarAberta() ? 'close' : 'menu'" />
          </button>
        }
        <a class="brand" [routerLink]="inicio()">Estilo <em>Marcado</em></a>
      </div>
      @if (auth.sessao(); as sessao) {
        <span class="header-context">{{ nomePerfil(sessao.perfil) }}</span>
        <a class="icon-button notification-link" routerLink="/notificacoes"
          [attr.aria-label]="naoLidas() + ' notificações não lidas'">
          <app-icon name="mail" />
          @if (naoLidas() > 0) { <span class="notification-count" aria-live="polite">{{ naoLidas() }}</span> }
        </a>
        <div class="account-area" #accountArea>
          <button #accountTrigger class="avatar-trigger" type="button"
            aria-label="Abrir menu do usuário" aria-haspopup="menu"
            [attr.aria-expanded]="menuAberto()" (click)="alternarMenu()">
            <img src="/avatar.svg" alt="" width="36" height="36">
            <span class="account-name">{{ sessao.nome }}</span>
            <app-icon name="chevron" />
          </button>
          @if (menuAberto()) {
            <div class="account-menu" role="menu" aria-label="Menu do usuário">
              <a routerLink="/conta" role="menuitem" (click)="fecharMenu()">
                <app-icon name="user" /> Meu perfil
              </a>
              <button type="button" role="menuitem" (click)="sair()" [disabled]="saindo()">
                <app-icon name="logout" /> {{ saindo() ? 'Saindo…' : 'Sair' }}
              </button>
              @if (erroSaida()) { <p class="menu-error" role="alert">{{ erroSaida() }}</p> }
            </div>
          }
        </div>
      } @else {
        <nav class="public-nav" aria-label="Acesso">
          <a routerLink="/entrar">Entrar</a><a class="nav-cta" routerLink="/cadastro">Criar conta</a>
        </nav>
      }
    </header>

    @if (auth.sessao() && !paginaPublica()) {
      <div class="app-layout">
        @if (sidebarAberta()) {
          <button type="button" class="sidebar-scrim" aria-label="Fechar menu"
            (click)="sidebarAberta.set(false)"></button>
        }
        <aside id="app-sidebar" class="app-sidebar" [class.is-open]="sidebarAberta()">
          <div class="sidebar-intro">
            <span class="sidebar-monogram" aria-hidden="true">EM</span>
            <div><strong>Seu espaço</strong><small>{{ nomePerfil(auth.sessao()!.perfil) }}</small></div>
          </div>
          <nav class="sidebar-nav" aria-label="Navegação principal">
            @for (item of itens(); track item.texto) {
              <a [routerLink]="item.destino" [fragment]="item.fragmento"
                [class.is-active]="ativo(item)" [attr.aria-current]="ativo(item) ? 'page' : null"
                (click)="sidebarAberta.set(false)">
                <app-icon [name]="item.icone" /><span>{{ item.texto }}</span>
              </a>
            }
          </nav>
          <div class="sidebar-footer"><span class="sidebar-dot"></span> Estilo Marcado</div>
        </aside>
        <main class="app-main"><router-outlet /></main>
      </div>
    } @else {
      <main class="public-main"><router-outlet /></main>
      <footer class="public-footer">
        <div class="public-footer-inner">
          <div class="public-footer-intro">
            <strong>Estilo <em>Marcado</em></strong>
            <p>Seu horário de beleza, no seu tempo.</p>
          </div>
          <nav aria-label="Links do rodapé: explorar">
            <h2>Explorar</h2>
            <a routerLink="/filiais">Encontrar filiais</a>
            <a routerLink="/" fragment="como-funciona">Como funciona</a>
          </nav>
          <nav aria-label="Links do rodapé: conta">
            <h2>Conta</h2>
            <a routerLink="/entrar">Entrar</a>
            <a routerLink="/cadastro">Criar conta</a>
            <a routerLink="/recuperar-conta">Recuperar acesso</a>
          </nav>
        </div>
        <div class="public-footer-bottom">
          <span>Estilo Marcado</span>
          <a href="https://github.com/samuel06santos/dsai-ap1-EstiloMarcado"
            target="_blank" rel="noopener noreferrer" aria-label="Created by Samuel e Renan, abre o repositório no GitHub em nova aba">Created by Samuel e Renan</a>
        </div>
      </footer>
    }
  `
})
export class AppComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly estabelecimento = inject(EstabelecimentoService);
  @ViewChild('accountArea') private accountArea?: ElementRef<HTMLElement>;
  @ViewChild('accountTrigger') private accountTrigger?: ElementRef<HTMLButtonElement>;

  readonly menuAberto = signal(false);
  readonly sidebarAberta = signal(false);
  readonly saindo = signal(false);
  readonly erroSaida = signal('');
  readonly naoLidas = signal(0);
  readonly urlAtual = signal(this.router.url);
  readonly adminPrincipal = signal(false);
  readonly filialSelecionada = signal<number | null>(null);
  readonly paginaPublica = computed(() =>
    /^\/(filiais(?:\/|$)|entrar|cadastro|ativar|acao-email|recuperar-conta|redefinir-senha|convite|unidades\/)/
      .test(this.urlAtual()));
  readonly itens = computed<NavItem[]>(() => {
    const sessao = this.auth.sessao();
    if (!sessao) { return []; }
    switch (sessao.perfil) {
      case 'CLIENTE': return [
        { texto: 'Início', icone: 'home', destino: '/' },
        { texto: 'Meus agendamentos', icone: 'calendar', destino: '/meus-agendamentos' },
        { texto: 'Lista de espera', icone: 'clock', destino: '/lista-espera' },
        { texto: 'Notificações', icone: 'mail', destino: '/notificacoes' },
        ...(this.filialSelecionada() ? [{ texto: 'Filial e serviços', icone: 'scissors' as const,
          destino: `/unidades/${this.filialSelecionada()}` }] : []),
        { texto: 'Meu perfil', icone: 'user', destino: '/conta' }
      ];
      case 'PROFISSIONAL': return [
        { texto: 'Minha agenda', icone: 'calendar', destino: '/profissional/agenda' },
        { texto: 'Notificações', icone: 'mail', destino: '/notificacoes' },
        { texto: 'Minha disponibilidade', icone: 'clock', destino: '/profissional/disponibilidade' },
        { texto: 'Minha filial', icone: 'building', destino: '/minha-filial' },
        { texto: 'Meu perfil', icone: 'user', destino: '/conta' }
      ];
      case 'RECEPCAO': return [
        { texto: 'Agenda da filial', icone: 'calendar', destino: '/equipe/agendamentos' },
        { texto: 'Fila de espera', icone: 'clock', destino: '/equipe/lista-espera' },
        { texto: 'Notificações', icone: 'mail', destino: '/notificacoes' },
        { texto: 'Minha filial', icone: 'building', destino: '/minha-filial' },
        { texto: 'Meu perfil', icone: 'user', destino: '/conta' }
      ];
      case 'ADMINISTRADOR': return [
        { texto: 'Visão geral', icone: 'home', destino: '/administracao/estabelecimento' },
        { texto: 'Agendamentos', icone: 'calendar', destino: '/equipe/agendamentos' },
        { texto: 'Fila e encaixes', icone: 'clock', destino: '/equipe/lista-espera' },
        { texto: 'Relatórios', icone: 'sparkles', destino: '/administracao/relatorios' },
        { texto: 'Notificações', icone: 'mail', destino: '/notificacoes' },
        { texto: 'Minha filial', icone: 'building', destino: '/administracao/estabelecimento', fragmento: 'filial' },
        { texto: 'Profissionais', icone: 'scissors', destino: '/administracao/estabelecimento', fragmento: 'profissionais' },
        { texto: 'Equipe', icone: 'users', destino: '/administracao/usuarios' },
        { texto: 'Feriados e bloqueios', icone: 'clock', destino: '/administracao/agenda' },
        ...(this.adminPrincipal() ? [{ texto: 'Filiais', icone: 'building' as const,
          destino: '/administracao/estabelecimento', fragmento: 'filiais' }] : []),
        { texto: 'Meu perfil', icone: 'user', destino: '/conta' }
      ];
    }
  });

  constructor() {
    effect(onCleanup => {
      const sessao = this.auth.sessao();
      this.urlAtual();
      if (!sessao) { this.naoLidas.set(0); return; }
      const sub = this.http.get<{ naoLidas: number }>('/api/me/notificacoes/nao-lidas/contagem')
        .subscribe({ next: dados => this.naoLidas.set(dados.naoLidas),
          error: () => this.naoLidas.set(0) });
      onCleanup(() => sub.unsubscribe());
    });
    this.router.events.pipe(filter(evento => evento instanceof NavigationEnd)).subscribe(evento => {
      this.urlAtual.set(evento.urlAfterRedirects);
      this.menuAberto.set(false);
      this.sidebarAberta.set(false);
    });
    effect(onCleanup => {
      const sessao = this.auth.sessao();
      this.adminPrincipal.set(false);
      if (sessao?.perfil === 'ADMINISTRADOR' && sessao.unidadeId != null) {
        const sub = this.estabelecimento.filial(sessao.unidadeId).subscribe({
          next: filial => this.adminPrincipal.set(filial.principal),
          error: () => this.adminPrincipal.set(false)
        });
        onCleanup(() => sub.unsubscribe());
      }
    });
    effect(onCleanup => {
      const sessao = this.auth.sessao();
      const rota = this.urlAtual();
      if (sessao?.perfil !== 'CLIENTE') { this.filialSelecionada.set(null); return; }
      const rotaFilial = /^\/unidades\/(\d+)/.exec(rota);
      const salvo = typeof localStorage === 'undefined' ? null : localStorage.getItem('estilo-marcado-filial');
      const id = Number(rotaFilial?.[1] ?? salvo);
      if (!Number.isInteger(id) || id <= 0) { this.filialSelecionada.set(null); return; }
      const sub = this.estabelecimento.filialPublica(id).subscribe({
        next: () => {
          this.filialSelecionada.set(id);
          if (typeof localStorage !== 'undefined') { localStorage.setItem('estilo-marcado-filial', String(id)); }
        },
        error: () => {
          this.filialSelecionada.set(null);
          if (typeof localStorage !== 'undefined') { localStorage.removeItem('estilo-marcado-filial'); }
        }
      });
      onCleanup(() => sub.unsubscribe());
    });
  }

  ngOnInit(): void { this.auth.carregarSessao().subscribe(); }
  inicio(): string {
    switch (this.auth.sessao()?.perfil) {
      case 'PROFISSIONAL': return '/profissional/agenda';
      case 'RECEPCAO': return '/equipe/agendamentos';
      case 'ADMINISTRADOR': return '/administracao/estabelecimento';
      default: return '/';
    }
  }
  nomePerfil(perfil: Perfil): string {
    return { CLIENTE: 'Área do cliente', PROFISSIONAL: 'Área profissional',
      RECEPCAO: 'Recepção', ADMINISTRADOR: 'Administração' }[perfil];
  }
  ativo(item: NavItem): boolean {
    const atual = this.urlAtual().split('?')[0];
    return atual === item.destino + (item.fragmento ? `#${item.fragmento}` : '');
  }
  alternarMenu(): void { this.menuAberto.update(aberto => !aberto); this.erroSaida.set(''); }
  fecharMenu(): void { this.menuAberto.set(false); }
  alternarSidebar(): void { this.sidebarAberta.update(aberta => !aberta); }
  sair(): void {
    if (this.saindo()) { return; }
    this.saindo.set(true);
    this.erroSaida.set('');
    this.auth.logout().pipe(finalize(() => this.saindo.set(false))).subscribe({
      next: () => { this.fecharMenu(); void this.router.navigateByUrl('/'); },
      error: () => this.erroSaida.set('Não foi possível sair. Tente novamente.')
    });
  }
  @HostListener('document:click', ['$event'])
  cliqueFora(evento: MouseEvent): void {
    if (this.menuAberto() && this.accountArea &&
        !this.accountArea.nativeElement.contains(evento.target as Node)) { this.fecharMenu(); }
  }
  @HostListener('document:keydown.escape')
  escapar(): void {
    if (this.menuAberto()) { this.fecharMenu(); this.accountTrigger?.nativeElement.focus(); }
    this.sidebarAberta.set(false);
  }
}
