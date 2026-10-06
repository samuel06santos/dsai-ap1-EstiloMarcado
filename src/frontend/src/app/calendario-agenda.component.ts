import { ChangeDetectionStrategy, Component, EventEmitter, HostListener, Input, OnChanges,
  OnInit, Output, SimpleChanges } from '@angular/core';
import { AgendaItem } from './agenda-profissional.service';

export type VisaoAgenda = 'MES' | 'SEMANA' | 'DIA';

export interface CelulaMes {
  dia: string;
  numero: number;
  doMes: boolean;
  hoje: boolean;
  selecionado: boolean;
  total: number;
  estados: string[];
  eventos: AgendaItem[];
}

export interface DiaSemana {
  dia: string;
  nome: string;
  numero: number;
  hoje: boolean;
  selecionado: boolean;
}

export interface IntervaloAgenda {
  de: string;
  ate: string;
}

function isoDeData(data: Date): string {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-` +
    `${String(data.getDate()).padStart(2, '0')}`;
}

function dataDeIso(iso: string): Date {
  return new Date(`${iso}T12:00:00`);
}

function somarDias(iso: string, dias: number): string {
  const data = dataDeIso(iso);
  data.setDate(data.getDate() + dias);
  return isoDeData(data);
}

function inicioDaSemana(iso: string): string {
  const data = dataDeIso(iso);
  const deslocamento = (data.getDay() + 6) % 7;
  data.setDate(data.getDate() - deslocamento);
  return isoDeData(data);
}

function hojeIso(): string {
  return isoDeData(new Date());
}

function minutosDe(hora: string): number {
  const [h, m] = hora.split(':').map(Number);
  return (h || 0) * 60 + (m || 0);
}

@Component({
  selector: 'app-calendario-agenda',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="calendario" [attr.aria-busy]="carregando">
      <div class="cal-toolbar">
        <div class="cal-visoes" role="group" aria-label="Visão do calendário">
          @for (opcao of visoes; track opcao) {
            <button type="button" class="button ghost small"
              [class.is-active]="visao === opcao"
              [attr.aria-pressed]="visao === opcao"
              (click)="mudarVisao(opcao)">{{ rotuloVisao(opcao) }}</button>
          }
        </div>
        <div class="cal-navegacao">
          <button type="button" class="icon-button" aria-label="Período anterior" (click)="anterior()">‹</button>
          <span class="cal-periodo" aria-live="polite">{{ rotuloPeriodo() }}</span>
          <button type="button" class="icon-button" aria-label="Próximo período" (click)="proximo()">›</button>
          <button type="button" class="button ghost small" (click)="hoje()">Hoje</button>
        </div>
      </div>

      @if (visao === 'MES') {
        <div class="cal-mes" role="grid" aria-label="Visão mensal">
          <div class="cal-semana-cabecalho" role="row">
            @for (nome of nomesDias; track nome) {
              <span role="columnheader">{{ nome }}</span>
            }
          </div>
          <div class="cal-mes-corpo">
            @for (celula of celulasMes(); track celula.dia) {
              <button type="button" role="gridcell" class="cal-celula"
                [attr.id]="'cal-dia-' + celula.dia"
                [class.fora-do-mes]="!celula.doMes"
                [class.is-hoje]="celula.hoje"
                [class.is-selecionado]="celula.selecionado"
                [attr.aria-current]="celula.hoje ? 'date' : null"
                [attr.aria-selected]="celula.selecionado"
                [attr.aria-label]="rotuloCelula(celula)"
                (click)="selecionar(celula.dia)"
                (keydown)="tecla($event, celula.dia)">
                <span class="cal-celula-numero">{{ celula.numero }}</span>
                @if (celula.total) {
                  <span class="cal-celula-eventos" aria-hidden="true">
                    @for (item of celula.eventos; track item.id) {
                      <span [class]="'cal-mini-event estado-' + item.status.toLowerCase()">
                        {{ item.inicio.slice(11, 16) }} {{ item.servico }}
                      </span>
                    }
                  </span>
                  <span class="cal-celula-total">{{ celula.total }} atendimento{{ celula.total > 1 ? 's' : '' }}</span>
                  <span class="cal-pontos">
                    @for (estado of celula.estados; track estado) {
                      <i [class]="'cal-ponto estado-' + estado.toLowerCase()"></i>
                    }
                  </span>
                }
              </button>
            }
          </div>
        </div>
      } @else if (visao === 'SEMANA' && !estreito) {
        <div class="cal-semana" role="grid" aria-label="Visão semanal">
          <div class="cal-horas-cabecalho"></div>
          @for (dia of diasSemana(); track dia.dia) {
            <button type="button" class="cal-coluna-cabecalho" [attr.id]="'cal-dia-' + dia.dia"
              [class.is-hoje]="dia.hoje" [class.is-selecionado]="dia.selecionado"
              [attr.aria-selected]="dia.selecionado" (click)="selecionar(dia.dia)">
              <span>{{ dia.nome }}</span><strong>{{ dia.numero }}</strong>
            </button>
          }
          <div class="cal-horas">
            @for (hora of horas(); track hora) {
              <div class="cal-hora" [style.height.px]="alturaHoraPx()">{{ hora }}</div>
            }
          </div>
          @for (dia of diasSemana(); track dia.dia) {
            <div class="cal-coluna" [style.height.px]="alturaCorpoPx()">
              @for (item of itensDoDia(dia.dia); track item.id) {
                <button type="button" [class]="'cal-bloco estado-' + item.status.toLowerCase()"
                  [style.top.%]="topo(item)" [style.height.%]="altura(item)"
                  [attr.aria-label]="rotuloItem(item)" (click)="selecionarItem(item)">
                  <span>{{ item.inicio.slice(11, 16) }}–{{ item.fim.slice(11, 16) }}</span>
                  <strong>{{ item.servico }}</strong>
                </button>
              }
            </div>
          }
        </div>
      } @else {
        @if (visao === 'SEMANA') {
          <div class="cal-mobile-week" role="group" aria-label="Dias da semana">
            @for (dia of diasSemana(); track dia.dia) {
              <button type="button" [class.is-selecionado]="dia.selecionado"
                [attr.aria-pressed]="dia.selecionado" [attr.aria-label]="rotuloDiaSemana(dia)"
                (click)="selecionar(dia.dia)">
                <span>{{ dia.nome }}</span><strong>{{ dia.numero }}</strong>
                <small>{{ itensDoDia(dia.dia).length }}</small>
              </button>
            }
          </div>
        }
        <div class="cal-dia" role="list">
          @for (item of itensDoDia(diaSelecionado); track item.id) {
            <button type="button" role="listitem" class="cal-dia-item"
              [class.is-cancelled]="item.status === 'CANCELADO'"
              [attr.aria-label]="rotuloItem(item)" (click)="selecionarItem(item)">
              <span class="cal-dia-hora">{{ item.inicio.slice(11, 16) }}–{{ item.fim.slice(11, 16) }}</span>
              <span class="cal-dia-texto"><strong>{{ item.servico }}</strong><small>{{ item.cliente }}</small></span>
              <span [class]="'status-chip estado-' + item.status.toLowerCase()">{{ nomeStatus(item.status) }}</span>
            </button>
          } @empty {
            <p class="muted-copy">{{ carregando ? 'Carregando…' : 'Nenhum atendimento neste dia.' }}</p>
          }
        </div>
      }
    </div>
  `
})
export class CalendarioAgendaComponent implements OnChanges, OnInit {
  @Input() itens: AgendaItem[] = [];
  @Input() visao: VisaoAgenda = 'SEMANA';
  @Input() diaSelecionado = hojeIso();
  @Input() carregando = false;

  @Output() visaoChange = new EventEmitter<VisaoAgenda>();
  @Output() diaSelecionadoChange = new EventEmitter<string>();
  @Output() intervaloChange = new EventEmitter<IntervaloAgenda>();
  @Output() itemSelecionado = new EventEmitter<AgendaItem>();

  readonly visoes: VisaoAgenda[] = ['MES', 'SEMANA', 'DIA'];
  readonly nomesDias = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'];
  estreito = false;
  private inicializado = false;
  private ultimoPeriodo = '';

  ngOnInit(): void {
    this.estreito = this.calcularEstreito();
    this.emitirSeMudou(true);
    this.inicializado = true;
  }

  @HostListener('window:resize')
  aoRedimensionar(): void { this.estreito = this.calcularEstreito(); }

  private calcularEstreito(): boolean {
    return typeof window !== 'undefined' && window.innerWidth <= 767;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.inicializado) { return; }
    if (changes['visao'] || changes['diaSelecionado']) { this.emitirSeMudou(false); }
  }

  private emitirSeMudou(inicial: boolean): void {
    const periodo = this.periodoVisivel();
    const chave = `${periodo.de}|${periodo.ate}`;
    if (inicial || chave !== this.ultimoPeriodo) {
      this.ultimoPeriodo = chave;
      this.intervaloChange.emit(periodo);
    }
  }

  periodoVisivel(): IntervaloAgenda {
    if (this.visao === 'MES') {
      const base = dataDeIso(this.diaSelecionado);
      base.setDate(1);
      const inicio = inicioDaSemana(isoDeData(base));
      return { de: inicio, ate: somarDias(inicio, 41) };
    }
    if (this.visao === 'SEMANA') {
      const inicio = inicioDaSemana(this.diaSelecionado);
      return { de: inicio, ate: somarDias(inicio, 6) };
    }
    return { de: this.diaSelecionado, ate: this.diaSelecionado };
  }

  celulasMes(): CelulaMes[] {
    const base = dataDeIso(this.diaSelecionado);
    base.setDate(1);
    const mes = base.getMonth();
    const inicio = inicioDaSemana(isoDeData(base));
    const hoje = hojeIso();
    return Array.from({ length: 42 }, (_, indice) => {
      const dia = somarDias(inicio, indice);
      const data = dataDeIso(dia);
      const lista = this.itensDoDia(dia);
      return {
        dia,
        numero: data.getDate(),
        doMes: data.getMonth() === mes,
        hoje: dia === hoje,
        selecionado: dia === this.diaSelecionado,
        total: lista.length,
        estados: [...new Set(lista.map(item => item.status))],
        eventos: lista.slice(0, 2)
      };
    });
  }

  diasSemana(): DiaSemana[] {
    const inicio = inicioDaSemana(this.diaSelecionado);
    const hoje = hojeIso();
    return Array.from({ length: 7 }, (_, indice) => {
      const dia = somarDias(inicio, indice);
      return {
        dia,
        nome: this.nomesDias[indice],
        numero: dataDeIso(dia).getDate(),
        hoje: dia === hoje,
        selecionado: dia === this.diaSelecionado
      };
    });
  }

  itensDoDia(dia: string): AgendaItem[] {
    return this.itens
      .filter(item => item.inicio.slice(0, 10) === dia)
      .sort((a, b) => a.inicio.localeCompare(b.inicio));
  }

  private faixa(): { inicio: number; fim: number; total: number } {
    const pontos = this.itens.flatMap(item =>
      [minutosDe(item.inicio.slice(11, 16)), minutosDe(item.fim.slice(11, 16))]);
    let inicio = 8 * 60;
    let fim = 19 * 60;
    if (pontos.length) {
      inicio = Math.min(inicio, Math.floor(Math.min(...pontos) / 60) * 60);
      fim = Math.max(fim, Math.ceil(Math.max(...pontos) / 60) * 60);
    }
    inicio = Math.max(0, inicio);
    fim = Math.min(24 * 60, fim <= inicio ? inicio + 60 : fim);
    return { inicio, fim, total: fim - inicio };
  }

  horas(): string[] {
    const { inicio, fim } = this.faixa();
    const lista: string[] = [];
    for (let minuto = inicio; minuto < fim; minuto += 60) {
      const h = Math.floor(minuto / 60);
      lista.push(`${String(h).padStart(2, '0')}:00`);
    }
    return lista;
  }

  alturaHoraPx(): number { return 48; }
  alturaCorpoPx(): number { return this.horas().length * 48; }

  topo(item: AgendaItem): number {
    const { inicio, total } = this.faixa();
    return Math.max(0, ((minutosDe(item.inicio.slice(11, 16)) - inicio) / total) * 100);
  }

  altura(item: AgendaItem): number {
    const { total } = this.faixa();
    const duracao = minutosDe(item.fim.slice(11, 16)) - minutosDe(item.inicio.slice(11, 16));
    return Math.max(3, (duracao / total) * 100);
  }

  mudarVisao(visao: VisaoAgenda): void { this.visaoChange.emit(visao); }

  selecionar(dia: string): void { this.diaSelecionadoChange.emit(dia); }

  selecionarItem(item: AgendaItem): void { this.itemSelecionado.emit(item); }

  anterior(): void { this.deslocar(-1); }
  proximo(): void { this.deslocar(1); }
  hoje(): void { this.diaSelecionadoChange.emit(hojeIso()); }

  private deslocar(direcao: number): void {
    if (this.visao === 'MES') {
      const data = dataDeIso(this.diaSelecionado);
      data.setDate(1);
      data.setMonth(data.getMonth() + direcao);
      this.diaSelecionadoChange.emit(isoDeData(data));
    } else {
      this.diaSelecionadoChange.emit(somarDias(this.diaSelecionado, direcao * (this.visao === 'SEMANA' ? 7 : 1)));
    }
  }

  tecla(evento: KeyboardEvent, dia: string): void {
    const passos: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };
    const passo = passos[evento.key];
    if (passo == null) { return; }
    evento.preventDefault();
    const alvo = somarDias(dia, passo);
    this.diaSelecionadoChange.emit(alvo);
    setTimeout(() => document.getElementById('cal-dia-' + alvo)?.focus());
  }

  rotuloPeriodo(): string {
    if (this.visao === 'MES') {
      return this.capitalizar(new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' })
        .format(dataDeIso(this.diaSelecionado)));
    }
    if (this.visao === 'SEMANA') {
      const inicio = inicioDaSemana(this.diaSelecionado);
      const fim = somarDias(inicio, 6);
      const curto = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short' });
      return `${curto.format(dataDeIso(inicio))} – ${curto.format(dataDeIso(fim))}`;
    }
    return this.capitalizar(new Intl.DateTimeFormat('pt-BR',
      { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
      .format(dataDeIso(this.diaSelecionado)));
  }

  rotuloCelula(celula: CelulaMes): string {
    const data = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric',
      month: 'long', year: 'numeric' }).format(dataDeIso(celula.dia));
    return `${data}, ${celula.total} atendimento${celula.total === 1 ? '' : 's'}`;
  }

  rotuloDiaSemana(dia: DiaSemana): string {
    const data = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: '2-digit',
      month: '2-digit', year: 'numeric' }).format(dataDeIso(dia.dia));
    return `${data}, ${this.itensDoDia(dia.dia).length} atendimentos`;
  }

  rotuloItem(item: AgendaItem): string {
    return `${item.inicio.slice(11, 16)} às ${item.fim.slice(11, 16)}, ${item.servico}, ` +
      `${item.cliente}, ${this.nomeStatus(item.status)}`;
  }

  rotuloVisao(visao: VisaoAgenda): string {
    return { MES: 'Mês', SEMANA: 'Semana', DIA: 'Dia' }[visao];
  }

  nomeStatus(status: string): string {
    return { AGENDADO: 'Agendado', CONFIRMADO: 'Confirmado', CANCELADO: 'Cancelado' }[status] ?? status;
  }

  private capitalizar(texto: string): string {
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }
}
