import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { ConsultaHorarios, EstabelecimentoService, Filial, HorarioDisponivel, ServicoPublico } from './estabelecimento.service';

type Passo = 1 | 2 | 3;
type Periodo = 'todos' | 'manha' | 'tarde';
interface DiaResumo { data: string; vagas: number | null }

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  styleUrl: './jornada-guiada.css',
  template: `
    <section class="booking-shell">
      @if (erro()) {
        <div class="booking-unavailable" role="alert">
          <h1>Esta filial não está disponível.</h1>
          <p>{{ erro() }}</p>
          @if (erroRedeFilial()) {
            <button class="button ghost" type="button" (click)="carregarFilial()">Tentar novamente</button>
          }
          <a class="button primary" routerLink="/filiais" [queryParams]="retornoFiliais">Explorar filiais</a>
        </div>
      } @else if (filial(); as atual) {
        <header class="booking-heading">
          <p class="eyebrow">Agendamento na filial</p>
          <h1>{{ atual.nome }}</h1>
          @if (atual.endereco) { <p>{{ atual.endereco }}</p> }
          <p>Escolha seu atendimento em poucos passos. Nenhum horário é reservado até a confirmação.</p>
        </header>
        <nav aria-label="Progresso do agendamento" class="booking-progress">
          <ol>
            @for (nome of passos; track $index) {
              <li [class.is-current]="passo() === $index + 1"
                  [class.is-complete]="passo() > $index + 1"
                  [attr.aria-current]="passo() === $index + 1 ? 'step' : null">
                <span>{{ $index + 1 }}</span>{{ nome }}
              </li>
            }
          </ol>
        </nav>
        <div class="booking-layout">
          <div class="booking-step">
            @if (passo() === 1) {
              <p class="eyebrow">Passo 1 de 4</p>
              <h2>Qual serviço você procura?</h2>
              <p>Veja duração e preço antes de escolher.</p>
              @if (erroServicos()) {
                <p class="notice error" role="alert">{{ erroServicos() }}</p>
                <button class="button ghost" type="button" (click)="carregarServicos()">Tentar novamente</button>
              } @else if (!servicos().length) {
                <p role="status">Esta filial não tem serviços agendáveis no momento.</p>
                <a routerLink="/filiais">Escolher outra filial</a>
              } @else {
                <div class="booking-choices" role="group" aria-label="Escolher serviço">
                  @for (servico of servicos(); track servico.id) {
                    <button class="booking-choice" type="button"
                      [class.is-selected]="servicoSelecionado === servico.id"
                      [attr.aria-pressed]="servicoSelecionado === servico.id"
                      (click)="selecionarServico(servico)">
                      <strong>{{ servico.nome }}</strong>
                      @if (servico.descricao) { <small>{{ servico.descricao }}</small> }
                      <span>{{ servico.duracaoMinutos }} min · {{ servico.preco | currency:'BRL' }}</span>
                    </button>
                  }
                </div>
              }
            } @else if (passo() === 2) {
              <p class="eyebrow">Passo 2 de 4</p>
              <h2>Com quem você prefere agendar?</h2>
              <p>Escolha alguém ou veja as vagas de todos os profissionais habilitados.</p>
              <div class="booking-choices" role="group" aria-label="Escolher profissional">
                <button class="booking-choice" type="button"
                  [class.is-selected]="profissionalEscolhido && profissionalSelecionado === null"
                  [attr.aria-pressed]="profissionalEscolhido && profissionalSelecionado === null"
                  (click)="selecionarProfissional(null)">
                  <strong>Qualquer profissional disponível</strong>
                  <small>Você verá o nome de quem atenderá em cada horário.</small>
                </button>
                @for (profissional of profissionaisDoServico(); track profissional.id) {
                  <button class="booking-choice" type="button"
                    [class.is-selected]="profissionalEscolhido && profissionalSelecionado === profissional.id"
                    [attr.aria-pressed]="profissionalEscolhido && profissionalSelecionado === profissional.id"
                    (click)="selecionarProfissional(profissional.id)">
                    <strong>{{ profissional.nome }}</strong>
                    <small>Ver horários com este profissional</small>
                  </button>
                }
              </div>
            } @else {
              <p class="eyebrow">Passo 3 de 4</p>
              <h2>Qual dia e horário são melhores?</h2>
              <p>Os horários estão no fuso {{ atual.fusoHorario }} e podem mudar até a confirmação.</p>
              @if (avisoConflito()) {
                <p class="notice error" role="alert">Essa vaga acabou. Nenhuma reserva ou cobrança foi feita.
                  Escolha uma das alternativas abaixo.</p>
              }
              <h3>Próximos dias</h3>
              @if (carregandoDias()) { <p role="status">Procurando dias com vagas…</p> }
              <div class="booking-days" role="group" aria-label="Dias com disponibilidade">
                @for (dia of dias(); track dia.data) {
                  <button type="button" class="booking-day" [disabled]="dia.vagas === 0"
                    [class.is-selected]="dataSelecionada === dia.data"
                    [attr.aria-pressed]="dataSelecionada === dia.data"
                    (click)="selecionarDia(dia.data)">
                    <strong>{{ rotuloDia(dia.data) }}</strong>
                    <small>{{ dia.vagas === null ? 'Ver vagas' : dia.vagas === 1 ? '1 vaga' : dia.vagas + ' vagas' }}</small>
                  </button>
                }
              </div>
              <label class="booking-date-label">Outra data, até {{ dataMaxima() }}
                <input type="date" name="data" [(ngModel)]="dataSelecionada"
                  [min]="dataMinima()" [max]="dataMaxima()" (change)="consultarHorarios()">
              </label>
              @if (erroHorarios()) {
                <p class="notice error" role="alert">{{ erroHorarios() }}</p>
                <button class="button ghost" type="button" (click)="consultarHorarios()">Tentar novamente</button>
              }
              @if (carregandoHorarios()) { <p role="status">Consultando horários…</p> }
              @else if (consulta()) {
                @if (consulta()!.horarios.length) {
                  <div class="booking-periods" role="group" aria-label="Filtrar por período">
                    <button type="button" [class.is-selected]="periodo === 'todos'" (click)="periodo = 'todos'">Todos</button>
                    <button type="button" [class.is-selected]="periodo === 'manha'" (click)="periodo = 'manha'">Manhã</button>
                    <button type="button" [class.is-selected]="periodo === 'tarde'" (click)="periodo = 'tarde'">Tarde</button>
                  </div>
                  @if (horariosFiltrados().length) {
                    <div class="booking-slots" role="group" aria-label="Horários disponíveis">
                      @for (horario of horariosFiltrados(); track horario.inicio + '-' + horario.profissionalId) {
                        <button type="button" class="booking-slot"
                          [class.is-selected]="selecionado()?.inicio === horario.inicio &&
                            selecionado()?.profissionalId === horario.profissionalId"
                          [attr.aria-pressed]="selecionado()?.inicio === horario.inicio &&
                            selecionado()?.profissionalId === horario.profissionalId"
                          (click)="selecionarHorario(horario)">
                          <strong>{{ horario.inicio.slice(11, 16) }}–{{ horario.fim.slice(11, 16) }}</strong>
                          <small>{{ nomeProfissional(horario.profissionalId) }}</small>
                        </button>
                      }
                    </div>
                  } @else { <p role="status">Nenhuma vaga neste período. Veja o outro período.</p> }
                } @else {
                  <p role="status">Sem vagas nesse dia. Escolha outra data, profissional ou serviço.</p>
                  <div class="booking-alternatives">
                    <button class="button ghost" type="button" (click)="passo.set(2)">Mudar profissional</button>
                    <button class="button ghost" type="button" (click)="passo.set(1)">Mudar serviço</button>
                  </div>
                }
              }
            }
          </div>
          <aside class="booking-summary" aria-label="Resumo da escolha">
            <h2>Sua escolha</h2>
            <dl>
              <div><dt>Filial</dt><dd>{{ atual.nome }}</dd></div>
              <div><dt>Serviço</dt><dd>{{ servicoAtual()?.nome ?? 'Escolha um serviço' }}</dd></div>
              <div><dt>Profissional</dt><dd>{{ selecionado()
                ? nomeProfissional(selecionado()!.profissionalId)
                : profissionalEscolhido
                  ? profissionalSelecionado === null ? 'Qualquer disponível' : nomeProfissional(profissionalSelecionado)
                  : 'Escolha uma opção' }}</dd></div>
              <div><dt>Dia e horário</dt><dd>{{ selecionado()
                ? selecionado()!.inicio.slice(0, 16).replace('T', ' às ')
                : 'Escolha um horário' }}</dd></div>
            </dl>
          </aside>
          <div class="booking-actions">
            @if (passo() > 1) {
              <button type="button" class="button ghost" (click)="voltar()">Voltar</button>
            } @else {
              <a class="button ghost" routerLink="/filiais" [queryParams]="retornoFiliais">Ver outras filiais</a>
            }
            @if (passo() === 1) {
              <button type="button" class="button primary" [disabled]="servicoSelecionado === null"
                (click)="continuarServico()">Continuar</button>
            } @else if (passo() === 2) {
              <button type="button" class="button primary" [disabled]="!profissionalEscolhido"
                (click)="continuarProfissional()">Continuar</button>
            } @else {
              @if (selecionado(); as horario) {
                <a class="button primary" [routerLink]="['/unidades', atual.id, 'revisar']"
                  [queryParams]="parametrosRevisao(horario)">Revisar agendamento</a>
              } @else { <button type="button" class="button primary" disabled>Escolha um horário</button> }
            }
          </div>
        </div>
      } @else { <p class="booking-unavailable" role="status">Carregando filial…</p> }
    </section>
  `
})
export class FilialPublicaComponent implements OnInit {
  private readonly api = inject(EstabelecimentoService);
  private readonly route = inject(ActivatedRoute);
  readonly passos = ['Serviço', 'Profissional', 'Dia e horário', 'Revisão'];
  readonly filial = signal<Filial | null>(null);
  readonly servicos = signal<ServicoPublico[]>([]);
  readonly consulta = signal<ConsultaHorarios | null>(null);
  readonly selecionado = signal<HorarioDisponivel | null>(null);
  readonly dias = signal<DiaResumo[]>([]);
  readonly passo = signal<Passo>(1);
  readonly carregandoDias = signal(false);
  readonly carregandoHorarios = signal(false);
  readonly erro = signal('');
  readonly erroRedeFilial = signal(false);
  readonly erroServicos = signal('');
  readonly erroHorarios = signal('');
  readonly avisoConflito = signal(false);
  readonly retornoFiliais = {
    busca: this.route.snapshot.queryParamMap.get('busca') || null,
    servico: this.route.snapshot.queryParamMap.getAll('servico'),
    pagina: this.route.snapshot.queryParamMap.get('pagina') || 1
  };
  servicoSelecionado: number | null = null;
  profissionalSelecionado: number | null = null;
  profissionalEscolhido = false;
  dataSelecionada = '';
  periodo: Periodo = 'todos';
  private sequenciaConsulta = 0;
  private sequenciaDias = 0;
  private selecionadoEm = 0;
  private restaurarInicio = '';
  private restaurarProfissionalId = 0;

  ngOnInit(): void {
    this.carregarFilial();
  }

  carregarFilial(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id <= 0) { this.erro.set('Confira o endereço e escolha outra filial.'); return; }
    this.erro.set('');
    this.erroRedeFilial.set(false);
    this.api.filialPublica(id).subscribe({
      next: filial => {
        this.filial.set(filial);
        const data = this.route.snapshot.queryParamMap.get('data');
        this.dataSelecionada = data && this.dataValida(data) ? data : this.dataMinima();
        this.carregarServicos();
      },
      error: erro => {
        this.erroRedeFilial.set(!(erro instanceof HttpErrorResponse && erro.status === 404));
        this.erro.set(this.erroRedeFilial()
          ? 'Não foi possível carregar a filial. Tente novamente sem perder sua escolha.'
          : 'Ela pode ter sido desativada. Encontre outra filial para agendar.');
      }
    });
  }

  carregarServicos(): void {
    const filial = this.filial();
    if (!filial) return;
    this.erroServicos.set('');
    this.api.servicosDisponiveis(filial.id).subscribe({
      next: lista => {
        this.servicos.set(lista);
        const params = this.route.snapshot.queryParamMap;
        const servicoId = Number(params.get('servicoId'));
        this.servicoSelecionado = lista.find(item => item.id === servicoId)?.id ?? null;
        const preferenciaId = Number(params.get('preferenciaId'));
        const profissional = this.profissionaisDoServico().find(item => item.id === preferenciaId);
        if (profissional) { this.profissionalSelecionado = profissional.id; this.profissionalEscolhido = true; }
        else if (params.get('qualquer') === '1') {
          this.profissionalSelecionado = null; this.profissionalEscolhido = true;
        }
        const criadoEm = Number(params.get('criadoEm'));
        if (Number.isSafeInteger(criadoEm) && criadoEm <= Date.now() + 60_000
            && Date.now() - criadoEm <= 30 * 60_000) {
          this.restaurarInicio = params.get('inicio') ?? '';
          this.restaurarProfissionalId = Number(params.get('slotProfissionalId')) || 0;
          this.selecionadoEm = criadoEm;
        }
        const solicitado = Number(params.get('passo'));
        if (this.servicoSelecionado !== null && solicitado >= 2) this.passo.set(2);
        if (this.servicoSelecionado !== null && this.profissionalEscolhido && solicitado === 3) {
          this.passo.set(3);
          this.carregarDias();
          this.consultarHorarios();
        }
        this.avisoConflito.set(params.get('aviso') === 'horario-indisponivel');
      },
      error: () => this.erroServicos.set('Não foi possível carregar os serviços desta filial.')
    });
  }

  servicoAtual(): ServicoPublico | undefined {
    return this.servicos().find(item => item.id === this.servicoSelecionado);
  }

  profissionaisDoServico(): { id: number; nome: string }[] {
    return this.servicoAtual()?.profissionais.filter(item => item.ativo) ?? [];
  }

  nomeProfissional(id: number): string {
    return this.profissionaisDoServico().find(item => item.id === id)?.nome ?? 'Profissional indisponível';
  }

  selecionarServico(servico: ServicoPublico): void {
    if (this.servicoSelecionado !== servico.id) {
      this.servicoSelecionado = servico.id;
      this.profissionalSelecionado = null;
      this.profissionalEscolhido = false;
      this.limparHorario();
    }
  }

  continuarServico(): void { if (this.servicoSelecionado !== null) this.passo.set(2); }

  selecionarProfissional(id: number | null): void {
    this.profissionalSelecionado = id;
    this.profissionalEscolhido = true;
    this.limparHorario();
  }

  continuarProfissional(): void {
    if (!this.profissionalEscolhido) return;
    this.passo.set(3);
    if (this.consulta()) return;
    this.carregarDias();
    this.consultarHorarios();
  }

  voltar(): void { this.passo.set(this.passo() === 3 ? 2 : 1); }

  selecionarDia(data: string): void {
    this.dataSelecionada = data;
    this.avisoConflito.set(false);
    this.consultarHorarios();
  }

  carregarDias(): void {
    const filial = this.filial();
    if (!filial || this.servicoSelecionado === null) return;
    const sequencia = ++this.sequenciaDias;
    this.carregandoDias.set(true);
    const dataBase = this.dataMinima();
    const datas = Array.from({ length: 7 }, (_, indice) => this.somarDias(dataBase, indice));
    forkJoin(datas.map(data => this.api.horarios(filial.id, this.servicoSelecionado!, data,
      this.profissionalSelecionado ?? undefined).pipe(
        catchError(() => of(null))
      ))).subscribe(resultados => {
        if (sequencia !== this.sequenciaDias) return;
        this.dias.set(datas.map((data, indice) => ({
          data, vagas: resultados[indice]?.horarios.length ?? null
        })).sort((a, b) => (a.vagas ? 0 : 1) - (b.vagas ? 0 : 1) || a.data.localeCompare(b.data)));
        this.carregandoDias.set(false);
      });
  }

  consultarHorarios(): void {
    const filial = this.filial();
    if (!filial || this.servicoSelecionado === null || !this.dataValida(this.dataSelecionada)) {
      ++this.sequenciaConsulta;
      this.erroHorarios.set('Escolha uma data entre hoje e os próximos 60 dias.');
      this.consulta.set(null);
      this.selecionado.set(null);
      this.carregandoHorarios.set(false);
      return;
    }
    const sequencia = ++this.sequenciaConsulta;
    this.consulta.set(null);
    this.selecionado.set(null);
    this.erroHorarios.set('');
    this.carregandoHorarios.set(true);
    this.api.horarios(filial.id, this.servicoSelecionado, this.dataSelecionada,
      this.profissionalSelecionado ?? undefined).subscribe({
      next: resultado => {
        if (sequencia !== this.sequenciaConsulta) return;
        this.consulta.set(resultado);
        const restaurado = resultado.horarios.find(item => item.inicio === this.restaurarInicio
          && item.profissionalId === this.restaurarProfissionalId);
        if (restaurado) this.selecionado.set(restaurado);
        this.restaurarInicio = '';
        this.carregandoHorarios.set(false);
      },
      error: () => {
        if (sequencia !== this.sequenciaConsulta) return;
        this.erroHorarios.set('Não foi possível consultar os horários. Tente novamente.');
        this.carregandoHorarios.set(false);
      }
    });
  }

  horariosFiltrados(): HorarioDisponivel[] {
    return (this.consulta()?.horarios ?? []).filter(item => {
      const hora = Number(item.inicio.slice(11, 13));
      return this.periodo === 'todos' || (this.periodo === 'manha' ? hora < 12 : hora >= 12);
    });
  }

  selecionarHorario(horario: HorarioDisponivel): void {
    this.selecionado.set(horario);
    this.selecionadoEm = Date.now();
    this.avisoConflito.set(false);
  }

  parametrosRevisao(horario: HorarioDisponivel): Record<string, string | number | null> {
    return { servicoId: this.servicoSelecionado, profissionalId: horario.profissionalId,
      preferenciaId: this.profissionalSelecionado, inicio: horario.inicio,
      criadoEm: this.selecionadoEm || Date.now() };
  }

  dataMinima(): string {
    const fuso = this.filial()?.fusoHorario ?? 'America/Sao_Paulo';
    const partes = new Intl.DateTimeFormat('en-US', { timeZone: fuso,
      year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
    const valor = (tipo: string) => partes.find(item => item.type === tipo)?.value ?? '';
    return valor('year') + '-' + valor('month') + '-' + valor('day');
  }

  dataMaxima(): string { return this.somarDias(this.dataMinima(), 60); }

  rotuloDia(data: string): string {
    return new Intl.DateTimeFormat('pt-BR', { weekday: 'short', day: '2-digit', month: 'short',
      timeZone: 'UTC' }).format(new Date(data + 'T12:00:00Z'));
  }

  private dataValida(data: string): boolean {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(data) || data < this.dataMinima() || data > this.dataMaxima())
      return false;
    const [ano, mes, dia] = data.split('-').map(Number);
    const real = new Date(Date.UTC(ano, mes - 1, dia));
    return real.getUTCFullYear() === ano && real.getUTCMonth() + 1 === mes && real.getUTCDate() === dia;
  }

  private somarDias(data: string, dias: number): string {
    const valor = new Date(data + 'T12:00:00Z');
    valor.setUTCDate(valor.getUTCDate() + dias);
    return valor.toISOString().slice(0, 10);
  }

  private limparHorario(): void {
    ++this.sequenciaConsulta;
    ++this.sequenciaDias;
    this.selecionado.set(null);
    this.consulta.set(null);
    this.dias.set([]);
    this.carregandoHorarios.set(false);
    this.carregandoDias.set(false);
    this.erroHorarios.set('');
    this.restaurarInicio = '';
    this.restaurarProfissionalId = 0;
    this.selecionadoEm = 0;
  }
}
