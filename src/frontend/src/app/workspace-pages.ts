import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AgendaItem, AgendaProfissionalApi } from './agenda-profissional.service';
import { AuthService, MeuPerfil } from './auth.service';
import { CalendarioAgendaComponent, VisaoAgenda } from './calendario-agenda.component';
import { EstabelecimentoService, Filial } from './estabelecimento.service';
import { UiIconComponent } from './ui-icon.component';

@Component({
  standalone: true,
  imports: [RouterLink, UiIconComponent],
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Área interna</p><h1>Minha filial</h1>
        <p>Os dados do local ao qual sua conta está vinculada.</p></div>
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      @if (perfil(); as atual) {
        @if (atual.filial && atual.estabelecimento) {
          <div class="overview-grid">
            <article class="surface-panel" id="filial">
              <span class="panel-icon"><app-icon name="building" /></span>
              <p class="eyebrow">Filial</p><h2>{{ atual.filial.nome }}</h2>
              <span class="badge">{{ atual.filial.ativa ? 'Ativa' : 'Inativa' }}</span>
              @if (filial(); as dados) {
                <dl class="branch-details">
                  <div><dt>Endereço</dt><dd>{{ dados.endereco || 'Não informado' }}</dd></div>
                  <div><dt>Telefone</dt><dd>{{ dados.telefone || 'Não informado' }}</dd></div>
                  <div><dt>Fuso horário</dt><dd>{{ dados.fusoHorario }}</dd></div>
                </dl>
              }
              @if (atual.filial.ativa) {
                <a class="text-link" [routerLink]="['/unidades', atual.filial.id]">Ver página pública <app-icon name="arrow" /></a>
              } @else { <p class="muted-copy">A página pública não está disponível enquanto a filial estiver inativa.</p> }
            </article>
            <article class="surface-panel" id="estabelecimento">
              <span class="panel-icon"><app-icon name="sparkles" /></span>
              <p class="eyebrow">Estabelecimento</p><h2>{{ atual.estabelecimento.nome }}</h2>
              <p class="muted-copy">Sua filial pertence a este estabelecimento.</p>
            </article>
          </div>
        }
      }
    </section>
  `
})
export class MinhaFilialComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly estabelecimento = inject(EstabelecimentoService);
  readonly perfil = signal<MeuPerfil | null>(null);
  readonly filial = signal<Filial | null>(null);
  readonly erro = signal('');
  ngOnInit(): void {
    forkJoin({ perfil: this.auth.meuPerfil(), filial: this.estabelecimento.minhaFilial() }).subscribe({
      next: resultado => { this.perfil.set(resultado.perfil); this.filial.set(resultado.filial); },
      error: error => this.erro.set(AuthService.mensagemErro(error))
    });
  }
}

@Component({
  standalone: true,
  imports: [UiIconComponent, CalendarioAgendaComponent],
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Área profissional</p><h1>Minha agenda</h1>
        <p>Visões diária, semanal e mensal dos seus atendimentos. Clique em um dia para ver os detalhes.</p></div>
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      <app-calendario-agenda
        [itens]="itens()" [visao]="visao()" [diaSelecionado]="dia()" [carregando]="carregando()"
        (visaoChange)="trocarVisao($event)"
        (diaSelecionadoChange)="selecionarDia($event)"
        (intervaloChange)="carregar($event.de, $event.ate)"
        (itemSelecionado)="abrirItem($event)" />
      <article class="surface-panel section-card">
        <div class="section-title"><div><p class="eyebrow">Dia selecionado</p>
          <h2>{{ rotuloDia() }}</h2></div><app-icon name="calendar" /></div>
        @if (carregando()) { <p class="muted-copy" role="status">Carregando agenda…</p> }
        @else if (!itensDoDia().length) {
          <div class="empty-state"><span class="panel-icon"><app-icon name="calendar" /></span>
            <h3>Nenhum atendimento neste dia</h3><p>Escolha outra data no calendário.</p></div>
        } @else {
          <div class="agenda-list">
            @for (item of itensDoDia(); track item.id) {
              <article class="agenda-item" [class.is-cancelled]="item.status === 'CANCELADO'">
                <div class="agenda-time"><app-icon name="clock" />
                  <strong>{{ item.inicio.slice(11, 16) }}–{{ item.fim.slice(11, 16) }}</strong></div>
                <div><h3>{{ item.servico }}</h3><p>{{ item.cliente }}</p></div>
                <span class="status-chip">{{ nomeStatus(item.status) }}</span>
              </article>
            }
          </div>
        }
        <p class="muted-copy">Horários no fuso da filial: {{ fuso() }}.</p>
        @if (itemSelecionado(); as item) {
          <div class="content-card" role="status">
            <h3>Atendimento #{{ item.id }}</h3>
            <p><strong>{{ item.servico }}</strong> · {{ item.cliente }}</p>
            <p>{{ item.inicio.slice(0, 10).split('-').reverse().join('/') }}
              · {{ item.inicio.slice(11, 16) }}–{{ item.fim.slice(11, 16) }} ({{ fuso() }})</p>
            <span class="status-chip">{{ nomeStatus(item.status) }}</span>
          </div>
        }
      </article>
    </section>
  `
})
export class AgendaProfissionalComponent implements OnInit {
  private readonly api = inject(AgendaProfissionalApi);
  private readonly estabelecimento = inject(EstabelecimentoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly itens = signal<AgendaItem[]>([]);
  readonly visao = signal<VisaoAgenda>('SEMANA');
  readonly dia = signal(this.hoje());
  readonly carregando = signal(false);
  readonly erro = signal('');
  readonly fuso = signal('America/Sao_Paulo');
  readonly itemSelecionado = signal<AgendaItem | null>(null);

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const dataParam = params.get('data');
    if (dataParam && /^\d{4}-\d{2}-\d{2}$/.test(dataParam)) { this.dia.set(dataParam); }
    const visaoParam = (params.get('visao') ?? '').toUpperCase();
    if (visaoParam === 'MES' || visaoParam === 'SEMANA' || visaoParam === 'DIA') {
      this.visao.set(visaoParam as VisaoAgenda);
    } else if (typeof window !== 'undefined' && window.innerWidth <= 767) {
      this.visao.set('DIA');
    }
    this.estabelecimento.minhaFilial().subscribe({
      next: filial => this.fuso.set(filial.fusoHorario), error: () => { }
    });
  }

  carregar(de: string, ate: string): void {
    this.carregando.set(true);
    this.erro.set('');
    this.api.agendaDoIntervalo(de, ate).subscribe({
      next: itens => { this.itens.set(itens); this.carregando.set(false); },
      error: error => { this.itens.set([]); this.erro.set(AuthService.mensagemErro(error));
        this.carregando.set(false); }
    });
  }

  trocarVisao(visao: VisaoAgenda): void { this.visao.set(visao); this.atualizarUrl(); }

  selecionarDia(dia: string): void {
    this.dia.set(dia);
    this.itemSelecionado.set(null);
    this.atualizarUrl();
  }

  abrirItem(item: AgendaItem): void { this.itemSelecionado.set(item); }

  itensDoDia(): AgendaItem[] {
    return this.itens().filter(item => item.inicio.slice(0, 10) === this.dia());
  }

  rotuloDia(): string {
    return this.capitalizar(new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric',
      month: 'long', year: 'numeric' }).format(new Date(`${this.dia()}T12:00:00`)));
  }

  nomeStatus(status: string): string {
    return { AGENDADO: 'Agendado', CONFIRMADO: 'Confirmado', CANCELADO: 'Cancelado' }[status] ?? status;
  }

  private atualizarUrl(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { data: this.dia(), visao: this.visao().toLowerCase() },
      replaceUrl: true
    });
  }

  private hoje(): string {
    const agora = new Date();
    return `${agora.getFullYear()}-${String(agora.getMonth() + 1).padStart(2, '0')}-` +
      `${String(agora.getDate()).padStart(2, '0')}`;
  }

  private capitalizar(texto: string): string {
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }
}
