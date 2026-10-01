import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, switchMap } from 'rxjs';
import { AuthService } from './auth.service';
import { EstabelecimentoService, HorarioDisponivel, Profissional, ServicoPublico } from './estabelecimento.service';

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
  minhasFiliais(): Observable<{ id: number; nome: string; ativa: boolean }[]> {
    return this.http.get<{ id: number; nome: string; ativa: boolean }[]>('/api/me/filiais');
  }
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
        <p class="field-help">Escolha a filial para carregar os serviços e profissionais. Se não encontrar a filial desejada, abra primeiro a página dela.</p>
        <form (ngSubmit)="entrar()">
          <div class="form-grid">
            <label>Filial<select name="unidadeId" required [(ngModel)]="unidadeId" (ngModelChange)="trocarFilial()">
              <option [ngValue]="null">Selecione</option>
              @for (f of filiais(); track f.id) { <option [ngValue]="f.id">{{ f.nome }}</option> }
            </select></label>
            <label>Serviço<select name="servicoId" required [(ngModel)]="servicoId" (ngModelChange)="trocarServico()">
              <option [ngValue]="null">Selecione</option>
              @for (s of servicos(); track s.id) { <option [ngValue]="s.id">{{ s.nome }}</option> }
            </select></label>
            <label>Profissional (opcional)<select name="profissionalId" [(ngModel)]="profissionalId">
              <option [ngValue]="null">Qualquer</option>
              @for (p of profissionais(); track p.id) { <option [ngValue]="p.id">{{ p.nome }}</option> }
            </select></label>
            <label>De <input type="date" required [(ngModel)]="dataInicio" name="dataInicio"></label>
            <label>Até <input type="date" required [(ngModel)]="dataFim" name="dataFim"></label>
            <label>Horário inicial <input type="time" [(ngModel)]="horaInicio" name="horaInicio"></label>
            <label>Horário final <input type="time" [(ngModel)]="horaFim" name="horaFim"></label>
          </div>
          <button class="button primary" type="submit" [disabled]="ocupado()">Salvar preferência</button>
        </form>
      </section>
      @if (mensagem()) { <p class="notice" role="status">{{ mensagem() }}</p> }
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      <section class="surface-panel"><h2>Minhas solicitações</h2>
        <p class="muted-copy">Acompanhe suas solicitações, veja ofertas de vaga e cancele quando quiser.</p>
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
  private readonly catalogo = inject(EstabelecimentoService);
  private readonly rota = inject(ActivatedRoute);
  readonly solicitacoes = signal<Solicitacao[]>([]);
  readonly ofertas = signal<Oferta[]>([]);
  readonly filiais = signal<{ id: number; nome: string }[]>([]);
  readonly servicos = signal<ServicoPublico[]>([]);
  readonly profissionais = signal<{ id: number; nome: string }[]>([]);
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
    this.carregarFiliais();
    if (this.unidadeId) { this.trocarFilial(); }
    this.carregar();
  }

  private carregarFiliais(): void {
    const opcoes = new Map<number, string>();
    const aplicar = () => this.filiais.set(
      [...opcoes].map(([id, nome]) => ({ id, nome })).sort((a, b) => a.nome.localeCompare(b.nome)));
    if (this.unidadeId) {
      this.catalogo.filialPublica(this.unidadeId).subscribe({
        next: filial => { opcoes.set(filial.id, filial.nome); aplicar(); }, error: () => { }
      });
    }
    this.api.minhasFiliais().subscribe({
      next: lista => { lista.forEach(filial => opcoes.set(filial.id, filial.nome)); aplicar(); },
      error: () => { }
    });
  }

  trocarFilial(): void {
    this.servicos.set([]);
    this.profissionais.set([]);
    if (!this.unidadeId) { this.servicoId = null; this.profissionalId = null; return; }
    const desejado = this.servicoId;
    this.catalogo.servicosDisponiveis(this.unidadeId).subscribe({
      next: dados => {
        this.servicos.set(dados);
        this.servicoId = dados.some(s => s.id === desejado) ? desejado : (dados[0]?.id ?? null);
        this.trocarServico();
      },
      error: () => this.erro.set('Não foi possível carregar os serviços desta filial.')
    });
  }

  trocarServico(): void {
    this.profissionalId = null;
    const servico = this.servicos().find(item => item.id === this.servicoId);
    this.profissionais.set(servico?.profissionais ?? []);
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
      <p class="muted-copy">Escolha quais avisos deseja receber por e-mail.</p>
      <form (ngSubmit)="salvarPreferencias()">
        <label class="checkbox-line"><input type="checkbox" name="lembretes" [(ngModel)]="lembretes">
          Receber lembretes de agendamento</label>
        <label class="checkbox-line"><input type="checkbox" name="avisosLista" [(ngModel)]="avisosLista">
          Receber avisos da lista de espera</label>
        <button class="button ghost" type="submit">Salvar preferências</button>
      </form>
    </section>
    <section class="surface-panel"><h2>Suas notificações</h2>
      <p class="muted-copy">Novidades sobre seus agendamentos e ofertas de vaga.</p>
      <div class="user-list">
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
    <section class="surface-panel"><h2>Linha do tempo</h2>
      <p class="muted-copy">Eventos deste atendimento, do mais antigo ao mais recente.</p>
      <div class="user-list">
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
    <section class="surface-panel"><p class="muted-copy">Solicitações de vaga da sua filial. Filtre por estado para
      acompanhar quem aguarda atendimento.</p><label>Estado da solicitação
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
            <button class="button ghost small" type="button" (click)="selecionar(s)">Encaixar</button>
          }
        </article>
      } @empty { <p class="muted-copy">Fila vazia.</p> }
    </div></section>
    @if (ofertasDaSelecionada().length) {
      <section class="surface-panel"><h2>Ofertas da solicitação</h2>
        <p class="muted-copy">Horários oferecidos ao cliente; a vaga só é reservada após a aceitação.</p>
        @for (o of ofertasDaSelecionada(); track o.id) {
          <p>{{ o.inicio }} ({{ o.fusoHorario }}) · {{ o.status }} · validade {{ o.expiraEm }}</p>
        }
      </section>
    }
    @if (selecionada(); as s) {
      <section class="surface-panel"><h2>Encaixe de {{ s.clienteNome }}</h2>
        <p class="muted-copy">Reserve um horário para este cliente somente com a autorização marcada abaixo.</p>
        <form (ngSubmit)="encaixar(s)"><div class="form-grid">
          <label>Profissional<select name="profissionalId" required [(ngModel)]="profissionalId"
            (ngModelChange)="consultarHorarios()">
            <option [ngValue]="null">Selecione</option>
            @for (p of profissionais(); track p.id) {
              @if (s.profissionalId === null || s.profissionalId === p.id) {
                <option [ngValue]="p.id">{{ p.nome }}</option>
              }
            }
          </select></label>
          <label>Data <input type="date" name="dataEncaixe" required [(ngModel)]="dataEncaixe"
            [min]="s.dataInicio" [max]="s.dataFim" (ngModelChange)="consultarHorarios()"></label>
          <label>Horário disponível<select name="inicio" required [(ngModel)]="inicio"
            [disabled]="carregandoHorarios() || !horarios().length">
            <option value="">Selecione</option>
            @for (h of horarios(); track h.inicio) {
              <option [value]="h.inicio">{{ h.inicio.slice(11, 16) }}</option>
            }
          </select></label>
        </div><label class="checkbox-line"><input type="checkbox" name="autorizada" required
          [(ngModel)]="autorizada"> Cliente autorizou este horário</label>
          @if (carregandoHorarios()) { <p class="field-help" role="status">Consultando horários…</p> }
          @else if (horariosConsultados() && !horarios().length) {
            <p class="field-help">Não há horários disponíveis nesta data para o profissional e a preferência do cliente.</p>
          }
          <button class="button primary" type="submit" [disabled]="enviando() || carregandoHorarios() || !inicio">
            {{ enviando() ? 'Confirmando…' : 'Confirmar encaixe' }}</button></form>
      </section>
    }
    @if (mensagem()) { <p class="notice" role="status">{{ mensagem() }}</p> }
    @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
  </div>`
})
export class FilaEquipeComponent {
  private readonly api = inject(OperacaoApi);
  private readonly auth = inject(AuthService);
  private readonly catalogo = inject(EstabelecimentoService);
  readonly solicitacoes = signal<Solicitacao[]>([]);
  readonly selecionada = signal<Solicitacao | null>(null);
  readonly ofertasDaSelecionada = signal<Oferta[]>([]);
  readonly profissionais = signal<Profissional[]>([]);
  readonly horarios = signal<HorarioDisponivel[]>([]);
  readonly carregandoHorarios = signal(false);
  readonly horariosConsultados = signal(false);
  readonly enviando = signal(false);
  readonly mensagem = signal(''); readonly erro = signal('');
  profissionalId: number | null = null; dataEncaixe = ''; inicio = '';
  autorizada = false; statusFiltro = 'ATIVA';
  private consultaAtual = 0;
  constructor() {
    const id = this.auth.sessao()?.unidadeId;
    if (id) { this.catalogo.profissionais(id).subscribe({ next: lista => this.profissionais.set(lista) }); }
    this.carregar();
  }
  carregar(): void { const id = this.auth.sessao()?.unidadeId;
    if (id) { this.api.fila(id, this.statusFiltro).subscribe({ next: dados => this.solicitacoes.set(dados),
      error: () => this.erro.set('Não foi possível carregar a fila.') }); } }
  verOfertas(s: Solicitacao): void { this.api.ofertasEquipe(s.unidadeId, s.id).subscribe({
    next: ofertas => this.ofertasDaSelecionada.set(ofertas),
    error: () => this.erro.set('Não foi possível consultar ofertas.')
  }); }
  selecionar(s: Solicitacao): void {
    this.consultaAtual++;
    this.selecionada.set(s);
    this.profissionalId = s.profissionalId;
    this.dataEncaixe = '';
    this.inicio = '';
    this.autorizada = false;
    this.horarios.set([]);
    this.horariosConsultados.set(false);
    this.carregandoHorarios.set(false);
    this.mensagem.set('');
    this.erro.set('');
  }
  consultarHorarios(): void {
    const consulta = ++this.consultaAtual;
    const s = this.selecionada();
    this.inicio = '';
    this.horarios.set([]);
    this.horariosConsultados.set(false);
    this.carregandoHorarios.set(false);
    if (!s || !this.profissionalId || !this.dataEncaixe) { return; }
    if (this.dataEncaixe < s.dataInicio || this.dataEncaixe > s.dataFim) {
      this.erro.set('Escolha uma data dentro da preferência do cliente.'); return;
    }
    this.erro.set('');
    this.carregandoHorarios.set(true);
    this.catalogo.horarios(s.unidadeId, s.servicoId, this.dataEncaixe, this.profissionalId)
      .subscribe({
        next: consultaHorarios => {
          if (consulta !== this.consultaAtual) { return; }
          this.horarios.set(consultaHorarios.horarios.filter(h =>
            h.profissionalId === this.profissionalId
            && (!s.horaInicio || (h.inicio.slice(11, 16) >= s.horaInicio.slice(0, 5)
              && h.fim.slice(11, 16) <= (s.horaFim ?? '').slice(0, 5)))));
          this.horariosConsultados.set(true);
          this.carregandoHorarios.set(false);
        },
        error: () => {
          if (consulta !== this.consultaAtual) { return; }
          this.erro.set('Não foi possível consultar os horários. Tente novamente.');
          this.carregandoHorarios.set(false);
        }
      });
  }
  encaixar(s: Solicitacao): void {
    if (this.enviando()) { return; }
    if (!this.profissionalId || !this.horarios().some(h => h.inicio === this.inicio)
        || !this.autorizada) {
      this.erro.set('Selecione um horário disponível e confirme a autorização do cliente.'); return;
    }
    this.enviando.set(true);
    this.erro.set('');
    this.api.encaixar(s.unidadeId, { listaEsperaId: s.id, profissionalId: this.profissionalId,
      inicio: this.inicio, autorizacaoConfirmada: true }).subscribe({
        next: () => {
          this.enviando.set(false);
          this.mensagem.set('Encaixe confirmado.'); this.selecionada.set(null); this.carregar();
        },
        error: e => {
          this.enviando.set(false);
          const codigo = (e as { error?: { codigo?: string } })?.error?.codigo;
          if (codigo === 'HORARIO_INDISPONIVEL') {
            this.consultarHorarios();
            this.erro.set('Este horário deixou de estar disponível. Escolha outro.');
          } else if (codigo === 'PREFERENCIA_INCOMPATIVEL') {
            this.erro.set('O horário não atende à preferência do cliente. Escolha outro.');
          } else if (codigo === 'SOLICITACAO_ENCERRADA') {
            this.erro.set('Esta solicitação já foi encerrada.'); this.carregar();
          } else {
            this.erro.set(AuthService.mensagemErro(e));
          }
        }
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
      <section class="surface-panel"><h2>Total do período</h2>
        <p class="muted-copy">Indicadores agregados da filial no intervalo escolhido. Fuso: {{ r.fusoHorario }}</p>
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
