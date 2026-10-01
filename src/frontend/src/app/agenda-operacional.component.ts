import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Agendamento, AgendamentoApi } from './agendamento.service';
import { AuthService } from './auth.service';
import { EstabelecimentoService, HorarioDisponivel, Profissional, ServicoPublico } from './estabelecimento.service';
import { UiIconComponent } from './ui-icon.component';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, UiIconComponent],
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Recepção</p><h1>Agenda da filial</h1>
        <p>Confirme atendimentos e organize novos horários.</p></div>
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      @if (sucesso()) { <p class="notice success" role="status">{{ sucesso() }}</p> }
      <section class="surface-panel section-card">
        <div class="section-title"><div><p class="eyebrow">Atendimentos</p><h2>Dia selecionado</h2></div>
          <app-icon name="calendar" /></div>
        <div class="form-grid">
          <label>Data <input type="date" [(ngModel)]="data" (change)="carregar()"></label>
          <label>Profissional
            <select [(ngModel)]="profissionalFiltro" (change)="carregar()">
              <option [ngValue]="null">Todos</option>
              @for (p of profissionais(); track p.id) { <option [ngValue]="p.id">{{ p.nome }}</option> }
            </select>
          </label>
        </div>
        @if (carregando()) { <p role="status">Carregando agenda…</p> }
        @else if (!itens().length) { <p class="muted-copy">Sem atendimentos nesta data.</p> }
        <div class="user-list">
          @for (item of itens(); track item.id) {
            <article>
              <div><strong>{{ item.inicio.slice(11,16) }} · {{ item.servicoNome }}</strong>
                <small>Cliente #{{ item.clienteId }} · {{ nomeProfissional(item.profissionalId) }}</small>
                <small>{{ item.status }} · {{ item.precoAcordado | currency:'BRL' }}</small>
                <a class="text-link" [routerLink]="['/agendamentos', item.id, 'historico']">Ver histórico</a>
              </div>
              @if (item.status !== 'CANCELADO' && item.inicio > agoraLocal(item.fusoHorario)) {
                <div class="form-actions">
                  @if (item.status === 'AGENDADO') {
                    <button class="button primary" type="button" [disabled]="enviando()"
                      (click)="confirmar(item)">Confirmar</button>
                  }
                  <button class="button ghost" type="button" [disabled]="enviando()"
                    (click)="prepararReagendamento(item)">Reagendar</button>
                  <button class="button ghost" type="button" [disabled]="enviando()"
                    (click)="cancelar(item)">Cancelar</button>
                </div>
              }
            </article>
          }
        </div>
      </section>

      @if (reagendando(); as item) {
        <section class="surface-panel section-card">
          <h2>Reagendar atendimento #{{ item.id }}</h2>
          <label>Nova data <input type="date" [(ngModel)]="dataNova" (change)="consultarReagendamento()"></label>
          <button class="button ghost" type="button" (click)="consultarReagendamento()">Atualizar horários</button>
          @if (alternativas().length) {
            <div class="slot-grid">
              @for (horario of alternativas(); track horario.inicio) {
                <button class="slot-option" type="button" [disabled]="enviando()"
                  (click)="reagendar(item, horario.inicio)">{{ horario.inicio.slice(11,16) }}</button>
              }
            </div>
          } @else { <p class="muted-copy">Sem horários livres para este profissional e serviço.</p> }
          <button class="button ghost" type="button" (click)="reagendando.set(null)">Fechar</button>
        </section>
      }

      <section class="surface-panel section-card">
        <div class="section-title"><div><p class="eyebrow">Novo</p><h2>Agendar pela recepção</h2></div>
          <app-icon name="clock" /></div>
        <div class="form-grid">
          <label>Serviço <select [(ngModel)]="servicoId" (change)="trocarServico()">
            @for (s of servicos(); track s.id) { <option [ngValue]="s.id">{{ s.nome }} · {{ s.preco | currency:'BRL' }}</option> }
          </select></label>
          <label>Profissional <select [(ngModel)]="profissionalId" (change)="consultarCriacao()">
            @for (p of habilitados(); track p.id) { <option [ngValue]="p.id">{{ p.nome }}</option> }
          </select></label>
          <label>Data <input type="date" [(ngModel)]="dataCriacao" (change)="consultarCriacao()"></label>
        </div>
        <button class="button ghost" type="button" (click)="consultarCriacao()">Consultar horários</button>
        @if (horariosCriacao().length) {
          <div class="slot-grid">
            @for (horario of horariosCriacao(); track horario.inicio) {
              <button class="slot-option" type="button" [class.selected]="inicioEscolhido === horario.inicio"
                (click)="inicioEscolhido = horario.inicio">{{ horario.inicio.slice(11,16) }}</button>
            }
          </div>
        } @else { <p class="muted-copy">Sem horários livres nessa data.</p> }
        <div class="form-grid">
          <label>Cliente já atendido (ID) <input type="number" min="1" [(ngModel)]="clienteId"
            [disabled]="!!clienteNome" placeholder="Opcional"></label>
          <label>Ou nome do cliente avulso <input [(ngModel)]="clienteNome" [disabled]="!!clienteId"
            minlength="2" maxlength="120" placeholder="Nome completo"></label>
          <label>Telefone do cliente avulso <input [(ngModel)]="clienteTelefone" maxlength="20"
            [disabled]="!!clienteId" placeholder="Opcional"></label>
        </div>
        <p class="muted-copy">Informe um cliente já atendido nesta filial ou crie um avulso.</p>
        <button class="button primary" type="button" (click)="criar()"
          [disabled]="enviando() || !inicioEscolhido || (!clienteId && !clienteNome)">
          {{ enviando() ? 'Agendando…' : 'Criar agendamento' }}</button>
      </section>
    </section>
  `
})
export class AgendaOperacionalComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly api = inject(AgendamentoApi);
  private readonly estabelecimentos = inject(EstabelecimentoService);
  readonly itens = signal<Agendamento[]>([]);
  readonly profissionais = signal<Profissional[]>([]);
  readonly servicos = signal<ServicoPublico[]>([]);
  readonly alternativas = signal<HorarioDisponivel[]>([]);
  readonly horariosCriacao = signal<HorarioDisponivel[]>([]);
  readonly reagendando = signal<Agendamento | null>(null);
  readonly carregando = signal(false);
  readonly enviando = signal(false);
  readonly erro = signal('');
  readonly sucesso = signal('');
  data = new Date().toISOString().slice(0, 10);
  dataNova = this.data;
  dataCriacao = this.data;
  profissionalFiltro: number | null = null;
  servicoId: number | null = null;
  profissionalId: number | null = null;
  inicioEscolhido = '';
  clienteId: number | null = null;
  clienteNome = '';
  clienteTelefone = '';
  private chaveCriacao = crypto.randomUUID();
  private pedidoDaChave = '';

  private unidadeId(): number { return this.auth.sessao()?.unidadeId ?? 0; }
  agoraLocal(fuso: string): string {
    return new Intl.DateTimeFormat('sv-SE', { timeZone: fuso, year: 'numeric', month: '2-digit',
      day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date()).replace(' ', 'T');
  }
  ngOnInit(): void {
    const id = this.unidadeId();
    if (!id) { this.erro.set('Filial não encontrada.'); return; }
    this.carregar();
    this.estabelecimentos.profissionais(id).subscribe({
      next: lista => this.profissionais.set(lista), error: e => this.erro.set(AuthService.mensagemErro(e))
    });
    this.estabelecimentos.servicosDisponiveis(id).subscribe({
      next: lista => { this.servicos.set(lista); this.servicoId = lista[0]?.id ?? null; this.trocarServico(); },
      error: e => this.erro.set(AuthService.mensagemErro(e))
    });
  }
  nomeProfissional(id: number): string {
    return this.profissionais().find(item => item.id === id)?.nome ?? `Profissional #${id}`;
  }
  habilitados(): { id: number; nome: string }[] {
    return this.servicos().find(item => item.id === this.servicoId)?.profissionais ?? [];
  }
  carregar(): void {
    if (!this.unidadeId() || !this.data) { return; }
    this.carregando.set(true); this.erro.set('');
    this.api.filial(this.unidadeId(), this.data, this.data, this.profissionalFiltro ?? undefined).subscribe({
      next: lista => { this.itens.set(lista); this.carregando.set(false); },
      error: e => { this.erro.set(AuthService.mensagemErro(e)); this.carregando.set(false); }
    });
  }
  trocarServico(): void {
    this.profissionalId = this.habilitados()[0]?.id ?? null;
    this.consultarCriacao();
  }
  consultarCriacao(): void {
    this.horariosCriacao.set([]); this.inicioEscolhido = '';
    if (!this.servicoId || !this.profissionalId || !this.dataCriacao) { return; }
    this.estabelecimentos.horarios(this.unidadeId(), this.servicoId, this.dataCriacao,
      this.profissionalId).subscribe({
        next: resultado => this.horariosCriacao.set(resultado.horarios),
        error: e => this.erro.set(AuthService.mensagemErro(e))
      });
  }
  criar(): void {
    if (!this.servicoId || !this.profissionalId || !this.inicioEscolhido) { return; }
    this.enviando.set(true); this.erro.set('');
    const cliente = this.clienteId ? { clienteId: this.clienteId }
      : { clienteNome: this.clienteNome, clienteTelefone: this.clienteTelefone || undefined };
    const pedido = { servicoId: this.servicoId, profissionalId: this.profissionalId,
      inicio: this.inicioEscolhido, ...cliente };
    const assinatura = JSON.stringify(pedido);
    if (assinatura !== this.pedidoDaChave) {
      this.chaveCriacao = crypto.randomUUID(); this.pedidoDaChave = assinatura;
    }
    this.api.criar(this.unidadeId(), pedido,
      this.chaveCriacao).subscribe({
        next: () => { this.sucesso.set('Agendamento criado.'); this.enviando.set(false);
          this.chaveCriacao = crypto.randomUUID(); this.pedidoDaChave = '';
          this.carregar(); this.consultarCriacao(); },
        error: e => { this.erro.set(AuthService.mensagemErro(e)); this.enviando.set(false);
          if ((e as { error?: { codigo?: string } })?.error?.codigo === 'HORARIO_INDISPONIVEL') {
            this.consultarCriacao();
          } }
      });
  }
  confirmar(item: Agendamento): void {
    this.enviando.set(true); this.erro.set('');
    this.api.confirmar(item.id).subscribe({
      next: () => { this.sucesso.set('Agendamento confirmado.'); this.enviando.set(false); this.carregar(); },
      error: e => { this.erro.set(AuthService.mensagemErro(e)); this.enviando.set(false); }
    });
  }
  cancelar(item: Agendamento): void {
    if (!window.confirm('Cancelar este agendamento?')) { return; }
    const motivo = window.prompt('Motivo do cancelamento (opcional):', '') ?? '';
    this.enviando.set(true); this.erro.set('');
    this.api.cancelar(item.id, motivo).subscribe({
      next: () => { this.sucesso.set('Agendamento cancelado.'); this.enviando.set(false); this.carregar(); },
      error: e => { this.erro.set(AuthService.mensagemErro(e)); this.enviando.set(false); }
    });
  }
  prepararReagendamento(item: Agendamento): void {
    this.reagendando.set(item); this.dataNova = this.data;
    this.consultarReagendamento();
  }
  consultarReagendamento(): void {
    const item = this.reagendando();
    if (!item) { return; }
    this.alternativas.set([]);
    this.api.horariosReagendamento(item.id, this.dataNova).subscribe({
        next: resultado => this.alternativas.set(resultado.horarios),
        error: e => this.erro.set(AuthService.mensagemErro(e))
      });
  }
  reagendar(item: Agendamento, inicio: string): void {
    this.enviando.set(true); this.erro.set('');
    this.api.reagendar(item.id, inicio).subscribe({
      next: () => { this.reagendando.set(null); this.sucesso.set('Agendamento reagendado.');
        this.enviando.set(false); this.carregar(); },
      error: e => { this.erro.set(AuthService.mensagemErro(e)); this.enviando.set(false);
        this.consultarReagendamento(); }
    });
  }
}
