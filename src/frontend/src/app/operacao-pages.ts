import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, switchMap } from 'rxjs';
import { AuthService } from './auth.service';
import { EstabelecimentoService, Profissional, ServicoPublico } from './estabelecimento.service';

interface Solicitacao { id: number; unidadeId: number; servicoId: number; profissionalId: number | null;
  dataInicio: string; dataFim: string; horaInicio: string | null; horaFim: string | null;
  status: string; criadoEm: string; clienteNome: string; telefoneContato: string | null;
  posicaoAproximada: number | null; }
interface Oferta { id: number; solicitacaoId: number; profissionalId: number; inicio: string;
  fusoHorario: string; status: string; expiraEm: string; }
interface Notificacao { id: number; tipo: string; referenciaTipo: string; referenciaId: number;
  criadoEm: string; lidoEm: string | null; }
interface Indicadores { agendados: number; confirmados: number; cancelados: number; criacoes: number;
  confirmacoes: number; cancelamentos: number; reagendamentos: number; encaixes: number;
  solicitacoesAtivas: number; }
interface Relatorio { geradoEm: string; fusoHorario: string; total: Indicadores;
  dias: { data: string; indicadores: Indicadores }[]; }
interface Evento { id: number; tipo: string; ocorridoEm: string; estadoAnterior: string | null;
  estadoNovo: string; inicioAnterior: string | null; inicioNovo: string; }
interface PreferenciasNotificacao { lembretes: boolean; avisosLista: boolean; }

export class OperacaoApi {
  private readonly http = inject(HttpClient);
  privadas(): Observable<Solicitacao[]> { return this.http.get<Solicitacao[]>('/api/me/lista-espera'); }
  ofertas(id: number): Observable<Oferta[]> { return this.http.get<Oferta[]>(`/api/me/lista-espera/${id}/ofertas`); }
  entrar(unidadeId: number, dados: object): Observable<Solicitacao> {
    return this.mutar(() => this.http.post<Solicitacao>(`/api/unidades/${unidadeId}/lista-espera`, dados));
  }
  cancelar(id: number): Observable<void> {
    return this.mutar(() => this.http.delete<void>(`/api/me/lista-espera/${id}`));
  }
  aceitar(id: number, ofertaId: number): Observable<object> {
    return this.mutar(() => this.http.post(`/api/me/lista-espera/${id}/ofertas/${ofertaId}/aceitacoes`, {},
      { headers: { 'Idempotency-Key': crypto.randomUUID() } }));
  }
  fila(unidadeId: number, status?: string): Observable<Solicitacao[]> {
    return this.http.get<Solicitacao[]>(`/api/unidades/${unidadeId}/lista-espera`,
      { params: status ? { status } : {} });
  }
  ofertasEquipe(unidadeId: number, id: number): Observable<Oferta[]> {
    return this.http.get<Oferta[]>(`/api/unidades/${unidadeId}/lista-espera/${id}/ofertas`);
  }
  encaixar(unidadeId: number, dados: object): Observable<object> {
    return this.mutar(() => this.http.post(`/api/unidades/${unidadeId}/encaixes`, dados,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } }));
  }
  notificacoes(): Observable<Notificacao[]> { return this.http.get<Notificacao[]>('/api/me/notificacoes'); }
  ler(id: number): Observable<void> {
    return this.mutar(() => this.http.patch<void>(`/api/me/notificacoes/${id}/leitura`, {}));
  }
  preferencias(): Observable<PreferenciasNotificacao> {
    return this.http.get<PreferenciasNotificacao>('/api/me/notificacoes/preferencias');
  }
  salvarPreferencias(dados: PreferenciasNotificacao): Observable<PreferenciasNotificacao> {
    return this.mutar(() => this.http.put<PreferenciasNotificacao>('/api/me/notificacoes/preferencias', dados));
  }
  eventos(id: number): Observable<Evento[]> {
    return this.http.get<Evento[]>(`/api/agendamentos/${id}/eventos`);
  }
  relatorio(unidadeId: number, de: string, ate: string,
    servicoId?: number | null, profissionalId?: number | null): Observable<Relatorio> {
    const params: Record<string, string> = { de, ate };
    if (servicoId) { params['servicoId'] = String(servicoId); }
    if (profissionalId) { params['profissionalId'] = String(profissionalId); }
    return this.http.get<Relatorio>(`/api/unidades/${unidadeId}/relatorios/operacionais`,
      { params });
  }
  private mutar<T>(acao: () => Observable<T>): Observable<T> {
    return this.http.get('/api/autenticacao/csrf').pipe(switchMap(() => acao()));
  }
}

@Component({
  selector: 'app-lista-espera', standalone: true, imports: [FormsModule],
  providers: [OperacaoApi], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-stack">
      <header class="page-heading"><p class="eyebrow">Sua oportunidade</p><h1>Lista de espera</h1>
        <p>Informe quando você pode vir. Um aviso de vaga não bloqueia o horário até a confirmação.</p></header>
      <section class="surface-panel" aria-labelledby="entrar-titulo">
        <h2 id="entrar-titulo">Criar ou atualizar solicitação</h2>
        <form (ngSubmit)="entrar()">
          <div class="form-grid">
            <label>Filial <input type="number" min="1" required [(ngModel)]="unidadeId" name="unidadeId"></label>
            <label>Serviço <input type="number" min="1" required [(ngModel)]="servicoId" name="servicoId"></label>
            <label>Profissional (opcional) <input type="number" min="1" [(ngModel)]="profissionalId" name="profissionalId"></label>
            <label>De <input type="date" required [(ngModel)]="dataInicio" name="dataInicio"></label>
            <label>Até <input type="date" required [(ngModel)]="dataFim" name="dataFim"></label>
            <label>Horário inicial <input type="time" [(ngModel)]="horaInicio" name="horaInicio"></label>
            <label>Horário final <input type="time" [(ngModel)]="horaFim" name="horaFim"></label>
          </div>
          <button class="button primary" type="submit" [disabled]="ocupado()">Salvar preferência</button>
        </form>
        <p class="field-help">Encontre os números de filial, serviço e profissional na página da filial.</p>
      </section>
      @if (mensagem()) { <p class="notice" role="status">{{ mensagem() }}</p> }
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      <section class="surface-panel"><h2>Minhas solicitações</h2>
        @if (solicitacoes().length === 0) { <p class="muted-copy">Você ainda não entrou em nenhuma lista.</p> }
        <div class="user-list">
          @for (s of solicitacoes(); track s.id) {
            <article><div><strong>Serviço {{ s.servicoId }} · filial {{ s.unidadeId }}</strong>
              <small>{{ s.dataInicio }} a {{ s.dataFim }} · {{ s.status }}
                @if (s.posicaoAproximada) { · posição aproximada {{ s.posicaoAproximada }} }
              </small></div>
              <div class="form-actions">
                <button class="button ghost small" type="button" (click)="verOfertas(s.id)">Ver ofertas</button>
                @if (s.status === 'ATIVA') {
                  <button class="button ghost small" type="button" (click)="cancelar(s.id)">Cancelar</button>
                }
              </div>
            </article>
          }
        </div>
        @for (o of ofertas(); track o.id) {
          <article class="notice"><strong>Vaga {{ o.inicio }} ({{ o.fusoHorario }})</strong>
            <span> · {{ o.status }} · válida até {{ o.expiraEm }}</span>
            @if (o.status === 'ENVIADA') {
              <button class="button primary small" type="button" (click)="aceitar(o)">Aceitar vaga</button>
            }
          </article>
        }
      </section>
    </div>`
})
export class ListaEsperaComponent {
  private readonly api = inject(OperacaoApi);
  private readonly rota = inject(ActivatedRoute);
  readonly solicitacoes = signal<Solicitacao[]>([]);
  readonly ofertas = signal<Oferta[]>([]);
  readonly mensagem = signal('');
  readonly erro = signal('');
  readonly ocupado = signal(false);
  unidadeId: number | null = null;
  servicoId: number | null = null;
  profissionalId: number | null = null;
  dataInicio = '';
  dataFim = '';
  horaInicio = '';
  horaFim = '';
  constructor() {
    const params = this.rota.snapshot.queryParamMap;
    const unidade = Number(params.get('unidadeId') ??
      (typeof localStorage === 'undefined' ? null : localStorage.getItem('estilo-marcado-filial')));
    const servico = Number(params.get('servicoId'));
    const profissional = Number(params.get('profissionalId'));
    this.unidadeId = unidade > 0 ? unidade : null;
    this.servicoId = servico > 0 ? servico : null;
    this.profissionalId = profissional > 0 ? profissional : null;
    this.dataInicio = params.get('dataInicio') ?? '';
    this.dataFim = this.dataInicio;
    this.carregar();
  }
  carregar(): void { this.api.privadas().subscribe({ next: dados => this.solicitacoes.set(dados),
    error: () => this.erro.set('Não foi possível carregar a lista.') }); }
  entrar(): void {
    if (!this.unidadeId || !this.servicoId) { this.erro.set('Informe filial e serviço.'); return; }
    this.ocupado.set(true); this.erro.set('');
    this.api.entrar(this.unidadeId, { servicoId: this.servicoId, profissionalId: this.profissionalId,
      dataInicio: this.dataInicio, dataFim: this.dataFim,
      horaInicio: this.horaInicio || null, horaFim: this.horaFim || null }).subscribe({
      next: () => { this.ocupado.set(false); this.mensagem.set('Preferência salva.'); this.carregar(); },
      error: () => { this.ocupado.set(false); this.erro.set('Verifique datas, serviço e filial.'); }
    });
  }
  cancelar(id: number): void {
    this.api.cancelar(id).subscribe({ next: () => this.carregar(),
      error: () => this.erro.set('Não foi possível cancelar a solicitação.') });
  }
  verOfertas(id: number): void {
    this.api.ofertas(id).subscribe({ next: ofertas => this.ofertas.set(ofertas),
      error: () => this.erro.set('Não foi possível carregar ofertas.') });
  }
  aceitar(oferta: Oferta): void {
    this.api.aceitar(oferta.solicitacaoId, oferta.id).subscribe({
      next: () => { this.mensagem.set('Agendamento realizado. Confira seus agendamentos.'); this.carregar();
        this.verOfertas(oferta.solicitacaoId); },
      error: () => { this.erro.set('A vaga não está mais disponível. Suas preferências foram preservadas.');
        this.verOfertas(oferta.solicitacaoId); this.carregar(); }
    });
  }
}

@Component({
  selector: 'app-notificacoes', standalone: true, imports: [FormsModule], providers: [OperacaoApi],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page-stack"><header class="page-heading"><p class="eyebrow">Atualizações</p>
    <h1>Notificações</h1><p>Veja novidades sobre seus agendamentos e ofertas.</p></header>
    <section class="surface-panel"><h2>Preferências</h2>
      <form (ngSubmit)="salvarPreferencias()">
        <label class="checkbox-line"><input type="checkbox" name="lembretes" [(ngModel)]="lembretes">
          Receber lembretes de agendamento</label>
        <label class="checkbox-line"><input type="checkbox" name="avisosLista" [(ngModel)]="avisosLista">
          Receber avisos da lista de espera</label>
        <button class="button ghost" type="submit">Salvar preferências</button>
      </form>
    </section>
    <section class="surface-panel"><div class="user-list">
      @for (item of itens(); track item.id) {
        <article><div><strong>{{ item.tipo }}</strong><small>{{ item.criadoEm }} ·
          {{ item.lidoEm ? 'Lida' : 'Não lida' }}</small></div>
          @if (!item.lidoEm) { <button class="button ghost small" type="button"
            (click)="ler(item.id)">Marcar como lida</button> }
        </article>
      } @empty { <p class="muted-copy">Nenhuma notificação por enquanto.</p> }
    </div></section>
    @if (mensagem()) { <p class="notice success" role="status">{{ mensagem() }}</p> }
    @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
  </div>`
})
export class NotificacoesComponent {
  private readonly api = inject(OperacaoApi);
  readonly itens = signal<Notificacao[]>([]);
  readonly erro = signal('');
  readonly mensagem = signal('');
  lembretes = true; avisosLista = true;
  constructor() { this.carregar(); this.api.preferencias().subscribe({
    next: p => { this.lembretes = p.lembretes; this.avisosLista = p.avisosLista; },
    error: () => this.erro.set('Não foi possível carregar preferências.')
  }); }
  carregar(): void { this.api.notificacoes().subscribe({ next: itens => this.itens.set(itens),
    error: () => this.erro.set('Não foi possível carregar notificações.') }); }
  ler(id: number): void { this.api.ler(id).subscribe({ next: () => this.carregar(),
    error: () => this.erro.set('Não foi possível marcar como lida.') }); }
  salvarPreferencias(): void {
    this.api.salvarPreferencias({ lembretes: this.lembretes, avisosLista: this.avisosLista })
      .subscribe({ next: () => this.mensagem.set('Preferências salvas.'),
        error: () => this.erro.set('Não foi possível salvar preferências.') });
  }
}

@Component({
  selector: 'app-historico-agendamento', standalone: true, imports: [RouterLink],
  providers: [OperacaoApi], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page-stack"><header class="page-heading"><p class="eyebrow">Agendamento</p>
    <h1>Histórico #{{ id }}</h1><p>Registro de criação, confirmação, cancelamento e reagendamento.</p></header>
    <section class="surface-panel"><div class="user-list">
      @for (evento of eventos(); track evento.id) {
        <article><div><strong>{{ evento.tipo }}</strong><small>{{ evento.ocorridoEm }} ·
          {{ evento.estadoAnterior || 'Novo' }} → {{ evento.estadoNovo }}</small>
          <small>Horário: {{ evento.inicioAnterior || '—' }} → {{ evento.inicioNovo }}</small></div></article>
      } @empty { <p class="muted-copy">Nenhum evento encontrado.</p> }
    </div></section>
    <a routerLink="/meus-agendamentos" class="button ghost">Voltar aos agendamentos</a>
    @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
  </div>`
})
export class HistoricoAgendamentoComponent {
  private readonly api = inject(OperacaoApi);
  private readonly rota = inject(ActivatedRoute);
  readonly id = Number(this.rota.snapshot.paramMap.get('id'));
  readonly eventos = signal<Evento[]>([]);
  readonly erro = signal('');
  constructor() { this.api.eventos(this.id).subscribe({ next: eventos => this.eventos.set(eventos),
    error: () => this.erro.set('Histórico não disponível para esta conta.') }); }
}

@Component({
  selector: 'app-fila-equipe', standalone: true, imports: [FormsModule], providers: [OperacaoApi],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page-stack"><header class="page-heading"><p class="eyebrow">Operação</p>
    <h1>Fila e encaixes</h1><p>Marque apenas com autorização expressa do cliente.</p></header>
    <section class="surface-panel"><label>Estado da solicitação
      <select name="status" [(ngModel)]="statusFiltro" (change)="carregar()">
        <option value="">Todos</option><option value="ATIVA">Ativa</option>
        <option value="ATENDIDA">Atendida</option><option value="CANCELADA">Cancelada</option>
        <option value="EXPIRADA">Expirada</option>
      </select></label><div class="user-list">
      @for (s of solicitacoes(); track s.id) {
        <article><div><strong>{{ s.clienteNome }}</strong><small>Serviço {{ s.servicoId }} ·
          {{ s.dataInicio }} a {{ s.dataFim }} · {{ s.status }} · {{ s.telefoneContato || 'Sem telefone' }}</small>
          <small>Entrada: {{ s.criadoEm }} · horário {{ s.horaInicio || 'livre' }}–{{ s.horaFim || 'livre' }}
            @if (s.posicaoAproximada) { · posição {{ s.posicaoAproximada }} }
          </small></div>
          <button class="button ghost small" type="button" (click)="verOfertas(s)">Ofertas</button>
          @if (s.status === 'ATIVA') {
            <button class="button ghost small" type="button" (click)="selecionada.set(s)">Encaixar</button>
          }
        </article>
      } @empty { <p class="muted-copy">Fila vazia.</p> }
    </div></section>
    @if (ofertasDaSelecionada().length) {
      <section class="surface-panel"><h2>Ofertas da solicitação</h2>
        @for (o of ofertasDaSelecionada(); track o.id) {
          <p>{{ o.inicio }} ({{ o.fusoHorario }}) · {{ o.status }} · validade {{ o.expiraEm }}</p>
        }
      </section>
    }
    @if (selecionada(); as s) {
      <section class="surface-panel"><h2>Encaixe de {{ s.clienteNome }}</h2>
        <form (ngSubmit)="encaixar(s)"><div class="form-grid">
          <label>Profissional <input type="number" min="1" name="profissionalId" required
            [(ngModel)]="profissionalId"></label>
          <label>Início <input type="datetime-local" name="inicio" required [(ngModel)]="inicio"></label>
        </div><label class="checkbox-line"><input type="checkbox" name="autorizada" required
          [(ngModel)]="autorizada"> Cliente autorizou este horário</label>
          <button class="button primary" type="submit">Confirmar encaixe</button></form>
      </section>
    }
    @if (mensagem()) { <p class="notice" role="status">{{ mensagem() }}</p> }
    @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
  </div>`
})
export class FilaEquipeComponent {
  private readonly api = inject(OperacaoApi);
  private readonly auth = inject(AuthService);
  readonly solicitacoes = signal<Solicitacao[]>([]);
  readonly selecionada = signal<Solicitacao | null>(null);
  readonly ofertasDaSelecionada = signal<Oferta[]>([]);
  readonly mensagem = signal(''); readonly erro = signal('');
  profissionalId: number | null = null; inicio = ''; autorizada = false; statusFiltro = 'ATIVA';
  constructor() { this.carregar(); }
  carregar(): void { const id = this.auth.sessao()?.unidadeId;
    if (id) { this.api.fila(id, this.statusFiltro).subscribe({ next: dados => this.solicitacoes.set(dados),
      error: () => this.erro.set('Não foi possível carregar a fila.') }); } }
  verOfertas(s: Solicitacao): void { this.api.ofertasEquipe(s.unidadeId, s.id).subscribe({
    next: ofertas => this.ofertasDaSelecionada.set(ofertas),
    error: () => this.erro.set('Não foi possível consultar ofertas.')
  }); }
  encaixar(s: Solicitacao): void {
    if (!this.profissionalId || !this.inicio || !this.autorizada) {
      this.erro.set('Profissional, horário e autorização são obrigatórios.'); return;
    }
    this.api.encaixar(s.unidadeId, { listaEsperaId: s.id, profissionalId: this.profissionalId,
      inicio: this.inicio, autorizacaoConfirmada: true }).subscribe({
        next: () => { this.mensagem.set('Encaixe confirmado.'); this.selecionada.set(null); this.carregar(); },
        error: () => this.erro.set('Horário indisponível ou preferência incompatível.')
      });
  }
}

@Component({
  selector: 'app-relatorio-operacional', standalone: true, imports: [FormsModule],
  providers: [OperacaoApi], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="page-stack"><header class="page-heading"><p class="eyebrow">Gestão</p>
    <h1>Relatório operacional</h1><p>Indicadores agregados da sua filial.</p></header>
    <section class="surface-panel"><form (ngSubmit)="carregar()"><div class="form-grid">
      <label>De <input type="date" name="de" required [(ngModel)]="de"></label>
      <label>Até <input type="date" name="ate" required [(ngModel)]="ate"></label>
      <label>Serviço <select name="servico" [(ngModel)]="servicoId">
        <option [ngValue]="null">Todos</option>
        @for (s of servicos(); track s.id) { <option [ngValue]="s.id">{{ s.nome }}</option> }
      </select></label>
      <label>Profissional <select name="profissional" [(ngModel)]="profissionalId">
        <option [ngValue]="null">Todos</option>
        @for (p of profissionais(); track p.id) { <option [ngValue]="p.id">{{ p.nome }}</option> }
      </select></label>
    </div><button class="button primary" type="submit">Atualizar relatório</button></form></section>
    @if (carregando()) { <p class="loading-state" role="status">Carregando indicadores…</p> }
    @if (relatorio(); as r) {
      <section class="surface-panel"><h2>Total do período</h2><p class="muted-copy">Fuso: {{ r.fusoHorario }}</p>
        <div class="overview-grid">
          <p>Agendados: <strong>{{ r.total.agendados }}</strong></p>
          <p>Confirmados: <strong>{{ r.total.confirmados }}</strong></p>
          <p>Cancelados: <strong>{{ r.total.cancelados }}</strong></p>
          <p>Encaixes: <strong>{{ r.total.encaixes }}</strong></p>
          <p>Solicitações ativas: <strong>{{ r.total.solicitacoesAtivas }}</strong></p>
          <p>Criações / confirmações / cancelamentos / reagendamentos:
            <strong>{{ r.total.criacoes }} / {{ r.total.confirmacoes }} /
              {{ r.total.cancelamentos }} / {{ r.total.reagendamentos }}</strong></p>
        </div>
        <p class="muted-copy">Estados contam reservas pelo dia atual da agenda. Atividades contam eventos
          pelo dia em que ocorreram. Encaixes são criações originadas da lista; solicitações ativas
          representam um retrato de agora e não são distribuídas por dia.</p>
        <h3>Por dia</h3><div class="user-list">
          @for (dia of r.dias; track dia.data) {
            <article><strong>{{ dia.data }}</strong><span>Agendados {{ dia.indicadores.agendados }} ·
              Confirmados {{ dia.indicadores.confirmados }} · Cancelados {{ dia.indicadores.cancelados }} ·
              Encaixes {{ dia.indicadores.encaixes }}</span></article>
          }
        </div>
      </section>
    }
    @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
  </div>`
})
export class RelatorioOperacionalComponent {
  private readonly api = inject(OperacaoApi);
  private readonly auth = inject(AuthService);
  private readonly catalogo = inject(EstabelecimentoService);
  readonly relatorio = signal<Relatorio | null>(null);
  readonly erro = signal('');
  readonly carregando = signal(false);
  readonly servicos = signal<ServicoPublico[]>([]);
  readonly profissionais = signal<Profissional[]>([]);
  de = new Date().toISOString().slice(0, 10);
  ate = this.de;
  servicoId: number | null = null; profissionalId: number | null = null;
  constructor() { const id = this.auth.sessao()?.unidadeId;
    if (id) {
      this.catalogo.servicosDisponiveis(id).subscribe({ next: dados => this.servicos.set(dados) });
      this.catalogo.profissionais(id).subscribe({ next: dados => this.profissionais.set(dados) });
    }
  }
  carregar(): void { const id = this.auth.sessao()?.unidadeId;
    if (!id) { this.erro.set('Filial não disponível.'); return; }
    this.carregando.set(true); this.erro.set('');
    this.api.relatorio(id, this.de, this.ate, this.servicoId, this.profissionalId).subscribe({
      next: dados => { this.relatorio.set(dados); this.carregando.set(false); },
      error: () => { this.carregando.set(false); this.erro.set('Verifique o período. O limite é 90 dias.'); }
    });
  }
}
