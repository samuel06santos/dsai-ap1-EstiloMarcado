import { CommonModule } from '@angular/common';
import { Component, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Agendamento, AgendamentoApi } from './agendamento.service';
import { AuthService } from './auth.service';
import { formatarData } from './data-apresentacao';
import { EstadoListaComponent } from './estado-lista.component';
import { FiltroAtivo, classificarLista, idUrlValido, orientacaoVazia, parametrosFiltrosUrl }
  from './estados-interface';
import { EstabelecimentoService, HorarioDisponivel, Profissional, ServicoPublico } from './estabelecimento.service';
import { UiIconComponent } from './ui-icon.component';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, UiIconComponent, EstadoListaComponent],
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Recepção</p><h1>Agenda da filial</h1>
        <p>Confirme atendimentos e organize novos horários.</p></div>
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      @if (sucesso()) { <p class="notice success" role="status">{{ sucesso() }}</p> }
      <section id="atendimentos" class="surface-panel section-card">
        <div class="section-title"><div><p class="eyebrow">Atendimentos</p><h2>Dia selecionado</h2></div>
          <app-icon name="calendar" /></div>
        <p class="muted-copy">Confirme atendimentos e acompanhe a agenda desta filial no dia escolhido.</p>
        <div class="form-grid">
          <label>Data <input #filtroData type="date" [ngModel]="data"
            (ngModelChange)="aplicarData($event)"></label>
          <label>Profissional
            <select [ngModel]="profissionalFiltro" (ngModelChange)="aplicarProfissional($event)">
              <option [ngValue]="null">Todos</option>
              @for (p of profissionais(); track p.id) { <option [ngValue]="p.id">{{ p.nome }}</option> }
            </select>
          </label>
        </div>
        @if (chipsFiltros().length) {
          <div class="filtros-resumo">
            <span class="eyebrow">Filtros ativos</span>
            @for (chip of chipsFiltros(); track chip.chave) {
              <span class="filtro-chip">{{ chip.rotulo }}: {{ chip.valor }}</span>
            }
            <button class="button ghost small" type="button" (click)="limparFiltros()">Limpar filtros</button>
          </div>
        }
        @switch (estadoAgenda()) {
          @case ('carregando') { <app-estado-lista [estado]="'carregando'" [titulo]="'Carregando agenda…'" /> }
          @case ('erro') {
            <app-estado-lista [estado]="'erro'" [titulo]="'Não foi possível carregar a agenda'"
              [descricao]="erroAgenda()" [icone]="'calendar'">
              <button class="button primary" type="button" (click)="carregar()">Tentar novamente</button>
            </app-estado-lista>
          }
          @case ('sem-dados') {
            <app-estado-lista [estado]="'sem-dados'" [titulo]="vazio().titulo"
              [descricao]="vazio().descricao" [icone]="'calendar'">
              <button class="button primary" type="button" (click)="agendarAtendimento()">Agendar atendimento</button>
              <button class="button ghost" type="button" (click)="focarPeriodo()">Alterar período</button>
            </app-estado-lista>
          }
          @case ('sem-resultado') {
            <app-estado-lista [estado]="'sem-resultado'" [titulo]="'Nenhum atendimento com este profissional'"
              [descricao]="'Ajuste o filtro ou o dia para ver outros atendimentos.'" [icone]="'calendar'">
              <button class="button primary" type="button" (click)="limparFiltros()">Limpar filtros</button>
              <button class="button ghost" type="button" (click)="agendarAtendimento()">Agendar atendimento</button>
            </app-estado-lista>
          }
        }
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

      <section id="novo-atendimento" class="surface-panel section-card">
        <div class="section-title"><div><p class="eyebrow">Novo</p><h2>Agendar pela recepção</h2></div>
          <app-icon name="clock" /></div>
        <p class="muted-copy">Escolha serviço, profissional e data para ver os horários livres e criar o agendamento.</p>
        <div class="form-grid">
          <label>Serviço <select [(ngModel)]="servicoId" (change)="trocarServico()">
            @for (s of servicos(); track s.id) { <option [ngValue]="s.id">{{ s.nome }} · {{ s.preco | currency:'BRL' }}</option> }
          </select></label>
          <label>Profissional <select [(ngModel)]="profissionalId" (change)="consultarCriacao()">
            @for (p of habilitados(); track p.id) { <option [ngValue]="p.id">{{ p.nome }}</option> }
          </select></label>
          <label>Data <input type="date" [(ngModel)]="dataCriacao" (change)="consultarCriacao()"></label>
        </div>
        <div style="display: grid; gap: 1rem; margin: .75rem 0 1rem;">
          <button class="button ghost" type="button" (click)="consultarCriacao()">Consultar horários</button>
          @if (horariosCriacao().length) {
            <div class="slot-grid">
              @for (horario of horariosCriacao(); track horario.inicio) {
                <button class="slot-option" type="button" [class.selected]="inicioEscolhido === horario.inicio"
                  (click)="inicioEscolhido = horario.inicio">{{ horario.inicio.slice(11,16) }}</button>
              }
            </div>
          } @else { <p class="muted-copy">Sem horários livres nessa data.</p> }
        </div>
        <div class="form-grid">
          <label>Cliente já atendido<select [(ngModel)]="clienteId" [disabled]="!!clienteNome">
            <option [ngValue]="null">Selecione</option>
            @for (c of clientes(); track c.id) { <option [ngValue]="c.id">{{ c.nome }}</option> }
          </select></label>
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
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  @ViewChild('filtroData') private filtroData?: ElementRef<HTMLInputElement>;
  readonly itens = signal<Agendamento[]>([]);
  readonly profissionais = signal<Profissional[]>([]);
  readonly servicos = signal<ServicoPublico[]>([]);
  readonly clientes = signal<{ id: number; nome: string }[]>([]);
  readonly alternativas = signal<HorarioDisponivel[]>([]);
  readonly horariosCriacao = signal<HorarioDisponivel[]>([]);
  readonly reagendando = signal<Agendamento | null>(null);
  readonly carregando = signal(false);
  readonly enviando = signal(false);
  readonly erro = signal('');
  readonly erroAgenda = signal('');
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
    this.lerFiltrosUrl();
    this.carregar();
    this.estabelecimentos.profissionais(id).subscribe({
      next: lista => {
        this.profissionais.set(lista);
        if (this.profissionalFiltro !== null
            && !lista.some(p => p.id === this.profissionalFiltro)) {
          this.profissionalFiltro = null;
          this.atualizarUrl();
          this.carregar();
        }
      },
      error: e => this.erro.set(AuthService.mensagemErro(e))
    });
    this.estabelecimentos.servicosDisponiveis(id).subscribe({
      next: lista => { this.servicos.set(lista); this.servicoId = lista[0]?.id ?? null; this.trocarServico(); },
      error: e => this.erro.set(AuthService.mensagemErro(e))
    });
    this.api.clientesDaUnidade(id).subscribe({
      next: lista => this.clientes.set(lista), error: () => { }
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
    this.carregando.set(true); this.erroAgenda.set('');
    this.api.filial(this.unidadeId(), this.data, this.data, this.profissionalFiltro ?? undefined).subscribe({
      next: lista => { this.itens.set(lista); this.carregando.set(false); },
      error: e => { this.erroAgenda.set(AuthService.mensagemErro(e)); this.carregando.set(false); }
    });
  }
  vazio() { return orientacaoVazia('recepcao-sem-atendimentos'); }
  chipsFiltros(): FiltroAtivo[] {
    const chips: FiltroAtivo[] = [{ chave: 'data', rotulo: 'Data', valor: formatarData(this.data) }];
    if (this.profissionalFiltro !== null) {
      chips.push({ chave: 'profissionalId', rotulo: 'Profissional',
        valor: this.nomeProfissional(this.profissionalFiltro) });
    }
    return chips;
  }
  estadoAgenda() {
    return classificarLista({ carregando: this.carregando(), erro: !!this.erroAgenda(),
      total: this.itens().length, filtrosAtivos: this.profissionalFiltro !== null ? 1 : 0 });
  }
  aplicarData(valor: string): void {
    if (!valor) { return; }
    this.data = valor;
    this.carregar();
    this.atualizarUrl();
  }
  aplicarProfissional(valor: number | null): void {
    this.profissionalFiltro = valor;
    this.carregar();
    this.atualizarUrl();
  }
  limparFiltros(): void {
    this.data = new Date().toISOString().slice(0, 10);
    this.profissionalFiltro = null;
    this.carregar();
    this.atualizarUrl();
  }
  agendarAtendimento(): void {
    const destino = typeof document === 'undefined' ? null : document.getElementById('novo-atendimento');
    destino?.scrollIntoView({ block: 'start' });
    (destino?.querySelector('select') as HTMLElement | null)?.focus();
  }
  focarPeriodo(): void { this.filtroData?.nativeElement.focus(); }
  private lerFiltrosUrl(): void {
    const params = this.rota.snapshot.queryParamMap;
    const data = params.get('data') ?? '';
    if (/^\d{4}-\d{2}-\d{2}$/.test(data)) { this.data = data; }
    this.profissionalFiltro = idUrlValido(params.get('profissionalId'));
  }
  private atualizarUrl(): void {
    void this.router.navigate([], { relativeTo: this.rota, replaceUrl: true,
      queryParams: parametrosFiltrosUrl({ data: this.data, profissionalId: this.profissionalFiltro }) });
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
