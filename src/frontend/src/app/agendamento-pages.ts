import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Observable, expand, forkJoin, reduce } from 'rxjs';
import { Agendamento, AgendamentoApi } from './agendamento.service';
import { AuthService } from './auth.service';
import { AgendaItem } from './agenda-profissional.service';
import { CalendarioAgendaComponent, VisaoAgenda } from './calendario-agenda.component';
import { formatarDataHora } from './data-apresentacao';
import { EstabelecimentoService, HorarioDisponivel } from './estabelecimento.service';
import { dividirPeriodoAgenda, FaixaAgenda } from './periodo-agenda';

function diaAtual(fuso = 'America/Sao_Paulo'): string {
  const partes = new Intl.DateTimeFormat('en-US', { timeZone: fuso,
    year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const valor = (tipo: string) => partes.find(item => item.type === tipo)?.value ?? '';
  return `${valor('year')}-${valor('month')}-${valor('day')}`;
}

function mensagem(erro: unknown): string { return AuthService.mensagemErro(erro); }

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, CalendarioAgendaComponent],
  styleUrl: './meus-agendamentos.css',
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Sua agenda</p><h1>Meus agendamentos</h1>
        <p>Explore seus horários por mês, semana ou dia e abra os detalhes de cada atendimento.</p></div>
      <section class="surface-panel section-card client-calendar-panel">
        @if (destacado(); as item) {
          <article class="notice success" aria-label="Agendamento solicitado">
            <strong>Agendamento #{{ item.id }} · {{ item.status }}</strong><br>
            {{ item.servicoNome }} em {{ formatarDataHora(item.inicio) }}<br>
            {{ item.unidadeNome ?? ('Filial #' + item.unidadeId) }} ·
            {{ item.profissionalNome ?? ('Profissional #' + item.profissionalId) }}<br>
            <a [routerLink]="['/agendamentos', item.id, 'historico']">Ver histórico deste agendamento</a>
          </article>
        }
        <div class="client-calendar-filters">
          <label>Filial<select [ngModel]="filtroUnidadeId()" (ngModelChange)="filtroUnidadeId.set($event)">
            <option [ngValue]="null">Todas</option>
            @for (opcao of unidadesDisponiveis(); track opcao.id) {
              <option [ngValue]="opcao.id">{{ opcao.nome }}</option>
            }
          </select></label>
          <label>Serviço<select [ngModel]="filtroServicoId()" (ngModelChange)="filtroServicoId.set($event)">
            <option [ngValue]="null">Todos</option>
            @for (opcao of servicosDisponiveis(); track opcao.id) {
              <option [ngValue]="opcao.id">{{ opcao.nome }}</option>
            }
          </select></label>
        </div>
        @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
        @if (mensagemSucesso()) { <p class="notice success" role="status">{{ mensagemSucesso() }}</p> }
        <app-calendario-agenda [itens]="itensAgenda()" [visao]="visao()"
          [diaSelecionado]="dia()" [carregando]="carregando()"
          (visaoChange)="visao.set($event)"
          (diaSelecionadoChange)="selecionarDia($event)"
          (intervaloChange)="carregar($event.de, $event.ate)"
          (itemSelecionado)="abrirItem($event)" />
      </section>
      <section class="surface-panel section-card client-day-panel" aria-label="Agendamentos do dia selecionado">
        <div class="client-day-heading">
          <div><p class="eyebrow">Dia selecionado</p><h2>{{ rotuloDia() }}</h2></div>
          <span>{{ itensDoDia().length }} agendamento{{ itensDoDia().length === 1 ? '' : 's' }}</span>
        </div>
        @if (carregando()) { <p role="status">Carregando agendamentos…</p> }
        @else if (!itensDoDia().length) {
          <p class="muted-copy">Nenhum agendamento neste dia. Selecione outra data no calendário.</p>
        }
        <div class="user-list">
          @for (item of itensDoDia(); track item.id) {
            <article [class.client-item-selected]="selecionado()?.id === item.id"
              [class.is-cancelled]="item.status === 'CANCELADO'">
              <div><strong>{{ item.servicoNome }}</strong>
                <small>{{ formatarDataHora(item.inicio) }} ·
                  {{ item.status }} · {{ item.precoAcordado | currency:'BRL' }}</small>
                <small>{{ item.unidadeNome ?? ('Filial #' + item.unidadeId) }}
                  · {{ item.profissionalNome ?? ('#' + item.profissionalId) }}</small>
                <small>Fuso: {{ item.fusoHorario }}</small>
                @if (item.motivoCancelamento) { <small>Motivo: {{ item.motivoCancelamento }}</small> }
              </div>
              <div class="form-actions">
                <a class="button ghost" [routerLink]="['/agendamentos', item.id, 'historico']">Histórico</a>
                @if (item.status !== 'CANCELADO' && item.inicio > agoraLocal(item.fusoHorario)) {
                  <button class="button ghost" type="button" [disabled]="enviando()"
                    (click)="iniciarReagendamento(item)">Reagendar</button>
                  <button class="button ghost" type="button" [disabled]="enviando()"
                    (click)="cancelar(item)">Cancelar</button>
                }
                @if (item.unidadeAtiva !== false && item.servicoAtivo !== false) {
                  <a class="button ghost" [routerLink]="['/unidades', item.unidadeId]"
                    [queryParams]="{ servicoId: item.servicoId }">Agendar novamente</a>
                }
              </div>
            </article>
          }
        </div>
        @if (reagendando(); as item) {
          <div class="surface-panel section-card">
            <h2>Escolha outro horário para #{{ item.id }}</h2>
            <label>Data <input type="date" [(ngModel)]="dataReagendamento" [min]="agoraLocal(item.fusoHorario).slice(0,10)"
              (change)="consultarAlternativas()"></label>
            <button type="button" class="button ghost" (click)="consultarAlternativas()">Atualizar horários</button>
            @if (alternativas().length) {
              <div class="slot-grid">
                @for (horario of alternativas(); track horario.inicio) {
                  <button class="slot-option" type="button" [disabled]="enviando()"
                    (click)="reagendar(item, horario.inicio)">{{ horario.inicio.slice(11,16) }}</button>
                }
              </div>
            } @else { <p class="muted-copy">Nenhum horário livre nessa data.</p> }
          </div>
        }
      </section>
    </section>
  `
})
export class MeusAgendamentosComponent implements OnInit {
  readonly formatarDataHora = formatarDataHora;
  private readonly api = inject(AgendamentoApi);
  private readonly estabelecimentos = inject(EstabelecimentoService);
  private readonly route = inject(ActivatedRoute);
  readonly todos = signal<Agendamento[]>([]);
  readonly filtroUnidadeId = signal<number | null>(null);
  readonly filtroServicoId = signal<number | null>(null);
  readonly itens = computed(() => this.todos().filter(item =>
    (this.filtroUnidadeId() === null || item.unidadeId === this.filtroUnidadeId())
    && (this.filtroServicoId() === null || item.servicoId === this.filtroServicoId())));
  readonly itensAgenda = computed<AgendaItem[]>(() => this.itens().map(item => ({
    id: item.id, inicio: item.inicio, fim: item.fim, servico: item.servicoNome,
    cliente: `${item.unidadeNome ?? `Filial #${item.unidadeId}`} · `
      + `${item.profissionalNome ?? `Profissional #${item.profissionalId}`}`,
    status: item.status
  })));
  readonly visao = signal<VisaoAgenda>('MES');
  readonly dia = signal(this.mesInicial());
  readonly selecionado = signal<Agendamento | null>(null);
  readonly destacado = signal<Agendamento | null>(null);
  readonly alternativas = signal<HorarioDisponivel[]>([]);
  readonly reagendando = signal<Agendamento | null>(null);
  readonly carregando = signal(false);
  readonly enviando = signal(false);
  readonly erro = signal('');
  readonly mensagemSucesso = signal('');
  private intervalo: FaixaAgenda | null = null;
  private sequenciaConsulta = 0;
  dataReagendamento = diaAtual();

  ngOnInit(): void {
    const id = Number(this.route.snapshot.queryParamMap.get('id'));
    if (Number.isSafeInteger(id) && id > 0) {
      this.api.detalhe(id).subscribe({
        next: item => { this.destacado.set(item); this.dia.set(item.inicio.slice(0, 10)); },
        error: () => { /* O calendário continua acessível sem o destaque. */ }
      });
    }
  }

  unidadesDisponiveis(): { id: number; nome: string }[] {
    const mapa = new Map<number, string>();
    this.todos().forEach(item => mapa.set(item.unidadeId,
      item.unidadeNome ?? `Filial #${item.unidadeId}`));
    return [...mapa].map(([id, nome]) => ({ id, nome }));
  }

  servicosDisponiveis(): { id: number; nome: string }[] {
    const mapa = new Map<number, string>();
    this.todos().forEach(item => mapa.set(item.servicoId, item.servicoNome));
    return [...mapa].map(([id, nome]) => ({ id, nome }));
  }
  agoraLocal(fuso: string): string {
    return new Intl.DateTimeFormat('sv-SE', { timeZone: fuso, year: 'numeric', month: '2-digit',
      day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date()).replace(' ', 'T');
  }
  private mesInicial(): string {
    const mes = this.route.snapshot.queryParamMap.get('mes') ?? '';
    return /^\d{4}-(0[1-9]|1[0-2])$/.test(mes) ? `${mes}-01` : diaAtual();
  }

  selecionarDia(dia: string): void {
    this.dia.set(dia);
    this.selecionado.set(null);
  }

  abrirItem(item: AgendaItem): void {
    this.dia.set(item.inicio.slice(0, 10));
    this.selecionado.set(this.itens().find(atual => atual.id === item.id) ?? null);
  }

  itensDoDia(): Agendamento[] {
    return this.itens().filter(item => item.inicio.slice(0, 10) === this.dia())
      .sort((a, b) => a.inicio.localeCompare(b.inicio));
  }

  rotuloDia(): string {
    const nome = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', timeZone: 'UTC' })
      .format(new Date(`${this.dia()}T12:00:00Z`));
    const data = this.dia().split('-').reverse().join('/');
    return `${nome.charAt(0).toUpperCase() + nome.slice(1)}, ${data}`;
  }

  carregar(de: string, ate: string): void {
    if (!this.intervalo || this.intervalo.de !== de || this.intervalo.ate !== ate) {
      this.todos.set([]);
      this.selecionado.set(null);
    }
    this.intervalo = { de, ate };
    this.recarregar();
  }

  private listarFaixa(faixa: FaixaAgenda): Observable<Agendamento[]> {
    return this.api.meus(faixa.de, faixa.ate).pipe(
      expand((pagina, indice) => pagina.length === 100
        ? this.api.meus(faixa.de, faixa.ate, { pagina: indice + 1 }) : EMPTY),
      reduce((todos, pagina) => todos.concat(pagina), [] as Agendamento[])
    );
  }

  private recarregar(): void {
    if (!this.intervalo) return;
    const faixas = dividirPeriodoAgenda(this.intervalo.de, this.intervalo.ate);
    if (!faixas.length) { this.erro.set('Período inválido.'); return; }
    const sequencia = ++this.sequenciaConsulta;
    this.carregando.set(true); this.erro.set('');
    forkJoin(faixas.map(faixa => this.listarFaixa(faixa))).subscribe({
      next: paginas => {
        if (sequencia !== this.sequenciaConsulta) return;
        this.todos.set(paginas.flat().sort((a, b) => a.inicio.localeCompare(b.inicio)));
        this.carregando.set(false);
      },
      error: e => {
        if (sequencia !== this.sequenciaConsulta) return;
        this.erro.set(mensagem(e)); this.carregando.set(false);
      }
    });
  }
  cancelar(item: Agendamento): void {
    if (!window.confirm('Cancelar este agendamento?')) { return; }
    this.enviando.set(true); this.erro.set('');
    this.api.cancelar(item.id, '').subscribe({
      next: () => { this.mensagemSucesso.set('Agendamento cancelado.'); this.enviando.set(false); this.recarregar(); },
      error: e => { this.erro.set(mensagem(e)); this.enviando.set(false); }
    });
  }
  iniciarReagendamento(item: Agendamento): void {
    this.reagendando.set(item); this.dataReagendamento = diaAtual(item.fusoHorario);
    this.consultarAlternativas();
  }
  consultarAlternativas(): void {
    const item = this.reagendando();
    if (!item) { return; }
    this.alternativas.set([]); this.erro.set('');
    this.api.horariosReagendamento(item.id, this.dataReagendamento).subscribe({
        next: resultado => this.alternativas.set(resultado.horarios),
        error: e => this.erro.set(mensagem(e))
      });
  }
  reagendar(item: Agendamento, inicio: string): void {
    this.enviando.set(true); this.erro.set('');
    this.api.reagendar(item.id, inicio).subscribe({
      next: () => { this.reagendando.set(null); this.enviando.set(false);
        this.mensagemSucesso.set('Agendamento reagendado.'); this.recarregar(); },
      error: e => { this.erro.set(mensagem(e)); this.enviando.set(false); this.consultarAlternativas(); }
    });
  }
}
