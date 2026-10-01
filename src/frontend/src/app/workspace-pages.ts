import { HttpClient } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService, MeuPerfil } from './auth.service';
import { EstabelecimentoService, Filial } from './estabelecimento.service';
import { UiIconComponent } from './ui-icon.component';

interface AtendimentoAgenda {
  id: number;
  inicio: string;
  servico: string;
  cliente: string;
  status: string;
}

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
  imports: [UiIconComponent],
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Área profissional</p><h1>Minha agenda</h1>
        <p>Atendimentos atribuídos a você, organizados por dia e horário.</p></div>
      <div class="surface-panel agenda-panel">
        <div class="agenda-toolbar">
          <div><span class="toolbar-label">Dia selecionado</span><h2>{{ diaLegivel() }}</h2></div>
          <div class="toolbar-actions">
            <button class="icon-button" type="button" aria-label="Dia anterior" (click)="mudarDia(-1)">‹</button>
            <button class="button ghost" type="button" (click)="voltarHoje()">Hoje</button>
            <button class="icon-button" type="button" aria-label="Próximo dia" (click)="mudarDia(1)">›</button>
          </div>
        </div>
        @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
        @if (carregando()) { <p class="muted-copy" role="status">Carregando agenda…</p> }
        @else if (!atendimentos().length) {
          <div class="empty-state"><span class="panel-icon"><app-icon name="calendar" /></span>
            <h3>Nenhum atendimento neste dia</h3><p>Use as setas para consultar outra data.</p></div>
        } @else {
          <div class="agenda-list">
            @for (item of atendimentos(); track item.id) {
              <article class="agenda-item" [class.is-cancelled]="item.status === 'CANCELADO'">
                <div class="agenda-time"><app-icon name="clock" /><strong>{{ item.inicio.slice(11, 16) }}</strong></div>
                <div><h3>{{ item.servico }}</h3><p>{{ item.cliente }}</p></div>
                <span class="status-chip">{{ nomeStatus(item.status) }}</span>
              </article>
            }
          </div>
        }
      </div>
    </section>
  `
})
export class AgendaProfissionalComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly dia = signal(this.isoLocal(new Date()));
  readonly atendimentos = signal<AtendimentoAgenda[]>([]);
  readonly carregando = signal(false);
  readonly erro = signal('');

  ngOnInit(): void { this.carregar(); }
  diaLegivel(): string {
    return new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
      .format(new Date(`${this.dia()}T12:00:00`));
  }
  mudarDia(dias: number): void {
    const data = new Date(`${this.dia()}T12:00:00`);
    data.setDate(data.getDate() + dias);
    this.dia.set(this.isoLocal(data));
    this.carregar();
  }
  voltarHoje(): void { this.dia.set(this.isoLocal(new Date())); this.carregar(); }
  nomeStatus(status: string): string {
    return { AGENDADO: 'Agendado', CONFIRMADO: 'Confirmado', CANCELADO: 'Cancelado' }[status] ?? status;
  }
  private carregar(): void {
    this.carregando.set(true); this.erro.set('');
    this.http.get<AtendimentoAgenda[]>('/api/painel/agenda', { params: { data: this.dia() } })
      .subscribe({
        next: itens => { this.atendimentos.set(itens); this.carregando.set(false); },
        error: error => { this.atendimentos.set([]); this.erro.set(AuthService.mensagemErro(error));
          this.carregando.set(false); }
      });
  }
  private isoLocal(data: Date): string {
    return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
  }
}
