import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Agendamento, AgendamentoApi } from './agendamento.service';
import { AuthService } from './auth.service';
import { EstabelecimentoService, Filial, HorarioDisponivel, ServicoPublico } from './estabelecimento.service';
import { UiIconComponent } from './ui-icon.component';

function diaAtual(fuso = 'America/Sao_Paulo'): string {
  const partes = new Intl.DateTimeFormat('en-US', { timeZone: fuso,
    year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const valor = (tipo: string) => partes.find(item => item.type === tipo)?.value ?? '';
  return `${valor('year')}-${valor('month')}-${valor('day')}`;
}

function mensagem(erro: unknown): string { return AuthService.mensagemErro(erro); }

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, UiIconComponent],
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Agendamento</p><h1>Revise seu horário</h1>
        <p>A reserva só será garantida quando você confirmar.</p></div>
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      @if (sucesso(); as reservado) {
        <article class="surface-panel section-card" role="status">
          <app-icon name="calendar" /><h2>Agendamento realizado</h2>
          <p>Comprovante #{{ reservado.id }} · {{ reservado.status }}</p>
          <p>{{ reservado.servicoNome }} · {{ reservado.inicio.slice(0, 16).replace('T', ' ') }}</p>
          <p>O próximo passo é aguardar a confirmação da equipe.</p>
          <a class="button primary" routerLink="/meus-agendamentos">Ver meus agendamentos</a>
        </article>
      } @else if (filial() && servico() && horarioDisponivel()) {
        <article class="surface-panel section-card">
          <div class="section-title"><div><p class="eyebrow">Sua escolha</p>
            <h2>{{ servico()!.nome }}</h2></div><app-icon name="scissors" /></div>
          <div class="overview-grid">
            <p><strong>Filial</strong><br>{{ filial()!.nome }}</p>
            <p><strong>Profissional</strong><br>{{ nomeProfissional() }}</p>
            <p><strong>Data e horário</strong><br>{{ inicio.slice(0, 16).replace('T', ' ') }}</p>
            <p><strong>Duração e valor</strong><br>{{ servico()!.duracaoMinutos }} min ·
              {{ servico()!.preco | currency:'BRL' }}</p>
          </div>
          <p class="muted-copy">Fuso horário: {{ filial()!.fusoHorario }}. A disponibilidade foi atualizada nesta página.</p>
          @if (auth.sessao()?.perfil === 'CLIENTE') {
            <button class="button primary" type="button" [disabled]="enviando()" (click)="confirmar()">
              {{ enviando() ? 'Reservando…' : 'Confirmar agendamento' }}</button>
          } @else if (!auth.sessao()) {
            <a class="button primary" [routerLink]="['/entrar']" [queryParams]="{ retorno: retorno() }">
              Entrar para continuar</a>
            <a class="button ghost" routerLink="/cadastro">Criar conta</a>
          } @else { <p class="notice">Entre com uma conta de cliente para reservar.</p> }
        </article>
      } @else if (carregando()) { <p role="status">Conferindo disponibilidade…</p> }
      <a [routerLink]="['/unidades', unidadeId]">Voltar aos horários</a>
    </section>
  `
})
export class RevisaoAgendamentoComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly estabelecimentos = inject(EstabelecimentoService);
  private readonly agendamentos = inject(AgendamentoApi);
  readonly auth = inject(AuthService);
  readonly filial = signal<Filial | null>(null);
  readonly servico = signal<ServicoPublico | null>(null);
  readonly horarioDisponivel = signal(false);
  readonly carregando = signal(true);
  readonly enviando = signal(false);
  readonly erro = signal('');
  readonly sucesso = signal<Agendamento | null>(null);
  readonly unidadeId = Number(this.route.snapshot.paramMap.get('id'));
  readonly servicoId = Number(this.route.snapshot.queryParamMap.get('servicoId'));
  readonly profissionalId = Number(this.route.snapshot.queryParamMap.get('profissionalId'));
  readonly inicio = this.route.snapshot.queryParamMap.get('inicio') ?? '';
  private readonly chaveReserva = crypto.randomUUID();

  ngOnInit(): void {
    if (![this.unidadeId, this.servicoId, this.profissionalId].every(id => Number.isInteger(id) && id > 0)
        || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(this.inicio)) {
      this.erro.set('Escolha um horário válido na página da filial.'); this.carregando.set(false); return;
    }
    this.estabelecimentos.filialPublica(this.unidadeId).subscribe({
      next: filial => this.filial.set(filial), error: e => this.erro.set(mensagem(e))
    });
    this.estabelecimentos.servicosDisponiveis(this.unidadeId).subscribe({
      next: servicos => this.servico.set(servicos.find(s => s.id === this.servicoId) ?? null),
      error: e => this.erro.set(mensagem(e))
    });
    this.atualizarHorario();
  }

  retorno(): string { return this.router.url; }
  nomeProfissional(): string {
    return this.servico()?.profissionais.find(p => p.id === this.profissionalId)?.nome ?? 'Profissional';
  }
  atualizarHorario(): void {
    this.carregando.set(true);
    this.estabelecimentos.horarios(this.unidadeId, this.servicoId, this.inicio.slice(0, 10),
      this.profissionalId).subscribe({
        next: consulta => {
          const livre = consulta.horarios.some(item => item.profissionalId === this.profissionalId
            && item.inicio === this.inicio);
          this.horarioDisponivel.set(livre);
          if (!livre) { this.erro.set('Este horário não está mais livre. Escolha outro na filial.'); }
          this.carregando.set(false);
        },
        error: e => { this.erro.set(mensagem(e)); this.carregando.set(false); }
      });
  }
  confirmar(): void {
    if (this.enviando() || !this.horarioDisponivel()) { return; }
    this.enviando.set(true); this.erro.set('');
    this.agendamentos.criar(this.unidadeId, { servicoId: this.servicoId,
      profissionalId: this.profissionalId, inicio: this.inicio }, this.chaveReserva).subscribe({
      next: reservado => { this.sucesso.set(reservado); this.enviando.set(false); },
      error: e => {
        this.erro.set(mensagem(e)); this.enviando.set(false);
        if ((e as { error?: { codigo?: string } })?.error?.codigo === 'HORARIO_INDISPONIVEL') {
          this.atualizarHorario();
        }
      }
    });
  }
}

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Sua agenda</p><h1>Meus agendamentos</h1>
        <p>Consulte seu histórico e cuide dos próximos horários.</p></div>
      <section class="surface-panel section-card">
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
  readonly itens = signal<Agendamento[]>([]);
  readonly alternativas = signal<HorarioDisponivel[]>([]);
  readonly reagendando = signal<Agendamento | null>(null);
  readonly carregando = signal(false);
  readonly enviando = signal(false);
  readonly erro = signal('');
  readonly mensagemSucesso = signal('');
  mes = diaAtual().slice(0, 7);
  dataReagendamento = diaAtual();
  filtroUnidadeId: number | null = null;
  filtroServicoId: number | null = null;

  ngOnInit(): void { this.carregar(); }

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
