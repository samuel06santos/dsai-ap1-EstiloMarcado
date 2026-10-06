import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Agendamento, AgendamentoApi, PainelCliente } from './agendamento.service';
import { AuthService } from './auth.service';
import { EstadoListaComponent } from './estado-lista.component';
import { orientacaoVazia } from './estados-interface';
import { HorarioDisponivel } from './estabelecimento.service';
import { formatarFuso } from './fuso-apresentacao';
import { UiIconComponent } from './ui-icon.component';

function diaNoFuso(fuso: string): string {
  const partes = new Intl.DateTimeFormat('en-US', { timeZone: fuso, year: 'numeric',
    month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const valor = (tipo: string) => partes.find(item => item.type === tipo)?.value ?? '';
  return `${valor('year')}-${valor('month')}-${valor('day')}`;
}

@Component({
  selector: 'app-painel-cliente',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, UiIconComponent, EstadoListaComponent],
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Área do cliente</p>
        <h1>{{ painel()?.cliente?.nome ? 'Olá, ' + painel()!.cliente.nome : 'Seu painel' }}</h1>
        <p>Seu próximo horário, seu histórico e os atalhos da sua conta.</p></div>

      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      @if (mensagemSucesso()) { <p class="notice success" role="status">{{ mensagemSucesso() }}</p> }

      @if (carregando()) {
        <p class="loading-state" role="status">Carregando seu painel…</p>
      } @else if (painel(); as dados) {
        @if (!dados.cliente.telefoneContato) {
          <p class="notice">Cadastre um telefone no seu perfil para que a equipe consiga falar com você.
            <a class="text-link" routerLink="/conta">Completar perfil <app-icon name="arrow" /></a></p>
        }

        @if (dados.proximo; as proximo) {
          <article class="surface-panel section-card">
            <div class="section-title"><div><p class="eyebrow">Próximo atendimento</p>
              <h2>{{ proximo.servicoNome }}</h2></div><app-icon name="calendar" /></div>
            <div class="overview-grid">
              <p><strong>Quando</strong><br>{{ dataHora(proximo) }}</p>
              <p><strong>Filial</strong><br>{{ nomeFilial(proximo) }}</p>
              <p><strong>Profissional</strong><br>{{ nomeProfissional(proximo) }}</p>
              <p><strong>Valor</strong><br>{{ proximo.precoAcordado | currency:'BRL' }}</p>
            </div>
            <span class="status-chip">{{ nomeStatus(proximo.status) }}</span>
            <div class="form-actions">
              <a class="button ghost" [routerLink]="['/agendamentos', proximo.id, 'historico']">Ver</a>
              <button class="button ghost" type="button" [disabled]="enviando()"
                (click)="iniciarReagendamento(proximo)">Reagendar</button>
              <button class="button ghost" type="button" [disabled]="enviando()"
                (click)="cancelar(proximo)">Cancelar</button>
              @if (podeRepetir(proximo)) {
                <a class="button primary" [routerLink]="['/unidades', proximo.unidadeId]"
                  [queryParams]="{ servicoId: proximo.servicoId }">Agendar novamente</a>
              }
            </div>
          </article>
        } @else {
          <app-estado-lista [estado]="'sem-dados'" [titulo]="vazio().titulo"
            [descricao]="vazio().descricao" [icone]="'calendar'">
            @if (filialRecente(); as filialId) {
              <a class="button primary" [routerLink]="['/unidades', filialId]">Encontrar horário</a>
            } @else {
              <a class="button primary" routerLink="/filiais">Encontrar filial</a>
            }
          </app-estado-lista>
        }

        @if (restantesProximos().length) {
          <article class="surface-panel section-card">
            <h2>Próximos horários</h2>
            <div class="user-list">
              @for (item of restantesProximos(); track item.id) {
                <article>
                  <div><strong>{{ item.servicoNome }}</strong>
                    <small>{{ dataHora(item) }} · {{ nomeStatus(item.status) }}</small>
                    <small>{{ nomeFilial(item) }} · {{ nomeProfissional(item) }}</small></div>
                  <div class="form-actions">
                    <button class="button ghost" type="button" [disabled]="enviando()"
                      (click)="iniciarReagendamento(item)">Reagendar</button>
                    <button class="button ghost" type="button" [disabled]="enviando()"
                      (click)="cancelar(item)">Cancelar</button>
                  </div>
                </article>
              }
            </div>
          </article>
        }

        <article class="surface-panel section-card">
          <h2>Resumo</h2>
          <div class="overview-grid">
            <p><strong>{{ dados.resumo.proximosAtivos }}</strong><br>Próximos atendimentos</p>
            <p><strong>{{ dados.resumo.realizados }}</strong><br>Realizados</p>
            <p><strong>{{ dados.resumo.cancelados }}</strong><br>Cancelados</p>
          </div>
        </article>

        <article class="surface-panel section-card">
          <div class="section-title"><div><p class="eyebrow">Sua trajetória</p>
            <h2>Histórico</h2></div><app-icon name="clock" /></div>
          @if (dados.historico.length) {
            <div class="user-list">
              @for (item of dados.historico; track item.id) {
                <article [class.is-cancelled]="item.status === 'CANCELADO'">
                  <div><strong>{{ item.servicoNome }}</strong>
                    <small>{{ dataHora(item) }} · {{ nomeStatus(item.status) }}
                      · {{ item.precoAcordado | currency:'BRL' }}</small>
                    <small>{{ nomeFilial(item) }} · {{ nomeProfissional(item) }}</small>
                    @if (item.motivoCancelamento) { <small>Motivo: {{ item.motivoCancelamento }}</small> }</div>
                  @if (podeRepetir(item)) {
                    <a class="button ghost" [routerLink]="['/unidades', item.unidadeId]"
                      [queryParams]="{ servicoId: item.servicoId }">Agendar novamente</a>
                  }
                </article>
              }
            </div>
          } @else { <p class="muted-copy">Seu histórico aparecerá aqui.</p> }
        </article>

        @if (reagendando(); as item) {
          <article class="surface-panel section-card">
            <h2>Escolha outro horário para #{{ item.id }}</h2>
            <label>Data <input type="date" [(ngModel)]="dataReagendamento"
              [min]="diaLocal(item.fusoHorario)" (change)="consultarAlternativas()"></label>
            @if (alternativas().length) {
              <div class="slot-grid">
                @for (horario of alternativas(); track horario.inicio) {
                  <button class="slot-option" type="button" [disabled]="enviando()"
                    (click)="reagendar(item, horario.inicio)">{{ horario.inicio.slice(11, 16) }}</button>
                }
              </div>
            } @else { <p class="muted-copy">Nenhum horário livre nessa data.</p> }
          </article>
        }
      }
    </section>
  `
})
export class PainelClienteComponent implements OnInit {
  private readonly api = inject(AgendamentoApi);

  readonly painel = signal<PainelCliente | null>(null);
  readonly carregando = signal(true);
  readonly enviando = signal(false);
  readonly erro = signal('');
  readonly mensagemSucesso = signal('');
  readonly alternativas = signal<HorarioDisponivel[]>([]);
  readonly reagendando = signal<Agendamento | null>(null);
  readonly filialRecente = signal<number | null>(null);

  vazio() { return orientacaoVazia('cliente-sem-agendamento'); }

  dataReagendamento = diaNoFuso('America/Sao_Paulo');

  ngOnInit(): void {
    this.filialRecente.set(this.lerFilialRecente());
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set('');
    this.api.painel().subscribe({
      next: dados => { this.painel.set(dados); this.carregando.set(false); },
      error: e => { this.erro.set(AuthService.mensagemErro(e)); this.carregando.set(false); }
    });
  }

  restantesProximos(): Agendamento[] {
    return (this.painel()?.proximos ?? []).slice(1);
  }

  nomeFilial(item: Agendamento): string {
    return item.unidadeNome ?? `Filial #${item.unidadeId}`;
  }

  nomeProfissional(item: Agendamento): string {
    return item.profissionalNome ?? `#${item.profissionalId}`;
  }

  nomeStatus(status: string): string {
    return { AGENDADO: 'Agendado', CONFIRMADO: 'Confirmado', CANCELADO: 'Cancelado' }[status] ?? status;
  }

  dataHora(item: Agendamento): string {
    return `${item.inicio.slice(0, 10).split('-').reverse().join('/')} ` +
      `${item.inicio.slice(11, 16)} (${formatarFuso(item.fusoHorario, item.inicio)})`;
  }

  podeRepetir(item: Agendamento): boolean {
    return item.unidadeAtiva !== false && item.servicoAtivo !== false;
  }

  diaLocal(fuso: string): string {
    return diaNoFuso(fuso);
  }

  cancelar(item: Agendamento): void {
    if (!window.confirm('Cancelar este agendamento?')) { return; }
    this.enviando.set(true);
    this.erro.set('');
    this.mensagemSucesso.set('');
    this.api.cancelar(item.id, '').subscribe({
      next: () => { this.enviando.set(false); this.mensagemSucesso.set('Agendamento cancelado.'); this.carregar(); },
      error: e => { this.enviando.set(false); this.erro.set(AuthService.mensagemErro(e)); }
    });
  }

  iniciarReagendamento(item: Agendamento): void {
    this.reagendando.set(item);
    this.dataReagendamento = diaNoFuso(item.fusoHorario);
    this.consultarAlternativas();
  }

  consultarAlternativas(): void {
    const item = this.reagendando();
    if (!item) { return; }
    this.alternativas.set([]);
    this.erro.set('');
    this.api.horariosReagendamento(item.id, this.dataReagendamento).subscribe({
      next: resultado => this.alternativas.set(resultado.horarios),
      error: e => this.erro.set(AuthService.mensagemErro(e))
    });
  }

  reagendar(item: Agendamento, inicio: string): void {
    this.enviando.set(true);
    this.erro.set('');
    this.api.reagendar(item.id, inicio).subscribe({
      next: () => {
        this.reagendando.set(null);
        this.enviando.set(false);
        this.mensagemSucesso.set('Agendamento reagendado.');
        this.carregar();
      },
      error: e => { this.enviando.set(false); this.erro.set(AuthService.mensagemErro(e)); this.consultarAlternativas(); }
    });
  }

  private lerFilialRecente(): number | null {
    if (typeof localStorage === 'undefined') { return null; }
    const valor = Number(localStorage.getItem('estilo-marcado-filial'));
    return Number.isInteger(valor) && valor > 0 ? valor : null;
  }
}
