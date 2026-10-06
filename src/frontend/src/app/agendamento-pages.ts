import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Agendamento, AgendamentoApi } from './agendamento.service';
import { AuthService } from './auth.service';
import { EstabelecimentoService, HorarioDisponivel } from './estabelecimento.service';

function diaAtual(fuso = 'America/Sao_Paulo'): string {
  const partes = new Intl.DateTimeFormat('en-US', { timeZone: fuso,
    year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const valor = (tipo: string) => partes.find(item => item.type === tipo)?.value ?? '';
  return `${valor('year')}-${valor('month')}-${valor('day')}`;
}

function mensagem(erro: unknown): string { return AuthService.mensagemErro(erro); }

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Sua agenda</p><h1>Meus agendamentos</h1>
        <p>Consulte seu histórico e cuide dos próximos horários.</p></div>
      <section class="surface-panel section-card">
        @if (destacado(); as item) {
          <article class="notice success" aria-label="Agendamento solicitado">
            <strong>Agendamento #{{ item.id }} · {{ item.status }}</strong><br>
            {{ item.servicoNome }} em {{ item.inicio.slice(0, 16).replace('T', ' às ') }}<br>
            {{ item.unidadeNome ?? ('Filial #' + item.unidadeId) }} ·
            {{ item.profissionalNome ?? ('Profissional #' + item.profissionalId) }}<br>
            <a [routerLink]="['/agendamentos', item.id, 'historico']">Ver histórico deste agendamento</a>
          </article>
        }
        <div class="form-grid">
          <label>Mês <input type="month" [(ngModel)]="mes" (change)="carregar()"></label>
          <label>Filial<select [(ngModel)]="filtroUnidadeId" (ngModelChange)="carregar()">
            <option [ngValue]="null">Todas</option>
            @for (opcao of unidadesDisponiveis(); track opcao.id) {
              <option [ngValue]="opcao.id">{{ opcao.nome }}</option>
            }
          </select></label>
          <label>Serviço<select [(ngModel)]="filtroServicoId" (ngModelChange)="carregar()">
            <option [ngValue]="null">Todos</option>
            @for (opcao of servicosDisponiveis(); track opcao.id) {
              <option [ngValue]="opcao.id">{{ opcao.nome }}</option>
            }
          </select></label>
        </div>
        @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
        @if (mensagemSucesso()) { <p class="notice success" role="status">{{ mensagemSucesso() }}</p> }
        @if (carregando()) { <p role="status">Carregando…</p> }
        @else if (!itens().length) { <p class="muted-copy">Nenhum agendamento neste mês.</p> }
        <div class="user-list">
          @for (item of itens(); track item.id) {
            <article>
              <div><strong>{{ item.servicoNome }}</strong>
                <small>{{ item.inicio.slice(0, 16).replace('T', ' ') }} ·
                  {{ item.status }} · {{ item.precoAcordado | currency:'BRL' }}</small>
                <small>{{ item.unidadeNome ?? ('Filial #' + item.unidadeId) }}
                  · {{ item.profissionalNome ?? ('#' + item.profissionalId) }}</small>
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
  private readonly api = inject(AgendamentoApi);
  private readonly estabelecimentos = inject(EstabelecimentoService);
  private readonly route = inject(ActivatedRoute);
  readonly itens = signal<Agendamento[]>([]);
  readonly destacado = signal<Agendamento | null>(null);
  readonly alternativas = signal<HorarioDisponivel[]>([]);
  readonly reagendando = signal<Agendamento | null>(null);
  readonly carregando = signal(false);
  readonly enviando = signal(false);
  readonly erro = signal('');
  readonly mensagemSucesso = signal('');
  mes = /^\d{4}-(0[1-9]|1[0-2])$/.test(this.route.snapshot.queryParamMap.get('mes') ?? '')
    ? this.route.snapshot.queryParamMap.get('mes')! : diaAtual().slice(0, 7);
  dataReagendamento = diaAtual();
  filtroUnidadeId: number | null = null;
  filtroServicoId: number | null = null;

  ngOnInit(): void {
    const id = Number(this.route.snapshot.queryParamMap.get('id'));
    if (Number.isSafeInteger(id) && id > 0) {
      this.api.detalhe(id).subscribe({
        next: item => { this.destacado.set(item); this.mes = item.inicio.slice(0, 7); this.carregar(); },
        error: () => this.carregar()
      });
    } else { this.carregar(); }
  }

  unidadesDisponiveis(): { id: number; nome: string }[] {
    const mapa = new Map<number, string>();
    this.itens().forEach(item => mapa.set(item.unidadeId,
      item.unidadeNome ?? `Filial #${item.unidadeId}`));
    return [...mapa].map(([id, nome]) => ({ id, nome }));
  }

  servicosDisponiveis(): { id: number; nome: string }[] {
    const mapa = new Map<number, string>();
    this.itens().forEach(item => mapa.set(item.servicoId, item.servicoNome));
    return [...mapa].map(([id, nome]) => ({ id, nome }));
  }
  agoraLocal(fuso: string): string {
    return new Intl.DateTimeFormat('sv-SE', { timeZone: fuso, year: 'numeric', month: '2-digit',
      day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date()).replace(' ', 'T');
  }
  carregar(): void {
    const [ano, mes] = this.mes.split('-').map(Number);
    if (!ano || !mes || mes < 1 || mes > 12) { this.erro.set('Mês inválido.'); return; }
    const de = `${this.mes}-01`;
    const ate = new Date(Date.UTC(ano, mes, 0)).toISOString().slice(0, 10);
    this.carregando.set(true); this.erro.set('');
    this.api.meus(de, ate, {
      unidadeId: this.filtroUnidadeId ?? undefined,
      servicoId: this.filtroServicoId ?? undefined
    }).subscribe({
      next: itens => { this.itens.set(itens); this.carregando.set(false); },
      error: e => { this.erro.set(mensagem(e)); this.carregando.set(false); }
    });
  }
  cancelar(item: Agendamento): void {
    if (!window.confirm('Cancelar este agendamento?')) { return; }
    this.enviando.set(true); this.erro.set('');
    this.api.cancelar(item.id, '').subscribe({
      next: () => { this.mensagemSucesso.set('Agendamento cancelado.'); this.enviando.set(false); this.carregar(); },
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
        this.mensagemSucesso.set('Agendamento reagendado.'); this.carregar(); },
      error: e => { this.erro.set(mensagem(e)); this.enviando.set(false); this.consultarAlternativas(); }
    });
  }
}
