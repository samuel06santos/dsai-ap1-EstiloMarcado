import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from './auth.service';
import {
  Afastamento, Bloqueio, DisponibilidadeService, ExcecaoJornada, Janela, JornadaIntervalo
} from './disponibilidade.service';
import { UiIconComponent } from './ui-icon.component';

function hojeIso(): string {
  const agora = new Date();
  return `${agora.getFullYear()}-${String(agora.getMonth() + 1).padStart(2, '0')}-${String(agora.getDate()).padStart(2, '0')}`;
}

function somarDias(dias: number): string {
  const data = new Date();
  data.setDate(data.getDate() + dias);
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

function formatarData(data: string): string {
  const [ano, mes, dia] = data.slice(0, 10).split('-');
  return `${dia}/${mes}/${ano}`;
}

@Component({
  standalone: true,
  imports: [FormsModule, UiIconComponent],
  template: `
    <style>
      .agenda-item { display: flex; justify-content: space-between; align-items: center; transition: box-shadow .2s ease; }
    </style>
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Área profissional</p><h1>Minha disponibilidade</h1>
        <p>Defina sua jornada, folgas, afastamentos e bloqueios. Essas regras determinam os horários que podem ser agendados.</p></div>

      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      @if (sucesso()) { <p class="notice success" role="status">{{ sucesso() }}</p> }

      <article class="surface-panel">
        <span class="panel-icon"><app-icon name="clock" /></span>
        <h2>Jornada semanal</h2>
        <p class="muted-copy">Cada dia aceita até quatro intervalos. Dias sem intervalo são dias não trabalhados.</p>
        @for (dia of dias; track dia.valor) {
          <div class="content-card" style="max-width: none; margin: .75rem 0;">
            <strong>{{ dia.nome }}</strong>
            @for (intervalo of intervalosDoDia(dia.valor); track intervalo) {
              <div class="jornada-intervalo">
                <input type="time" [(ngModel)]="intervalo.horaInicio" [name]="'i' + dia.valor + intervalo.horaInicio">
                <span class="jornada-ate">até</span>
                <input type="time" [(ngModel)]="intervalo.horaFim" [name]="'f' + dia.valor + intervalo.horaFim">
                <button type="button" class="button ghost small" (click)="removerIntervalo(intervalo)">Remover</button>
              </div>
            }
            <button type="button" class="button ghost small jornada-adicionar"
              (click)="adicionarIntervalo(dia.valor)">Adicionar intervalo</button>
          </div>
        }
        <div class="form-actions">
          <button type="button" class="button primary" [disabled]="salvando()" (click)="salvarJornada()">
            {{ salvando() ? 'Salvando…' : 'Salvar jornada' }}
          </button>
          <button type="button" class="button ghost" (click)="preverJanelas()">Ver prévia do dia</button>
        </div>
        <div class="form-grid" style="margin-top: 1rem;">
          <label>Data da prévia<input type="date" [(ngModel)]="previewData" name="previewData"></label>
        </div>
        @if (janelas().length || previewFeito()) {
          <div class="agenda-list">
            @for (janela of janelas(); track janela.inicio) {
              <article class="agenda-item"><div class="agenda-time"><app-icon name="clock" />
                <strong>{{ janela.inicio.slice(0, 5) }} – {{ janela.fim.slice(0, 5) }}</strong></div>
                <div><h3>Janela disponível</h3></div></article>
            } @empty {
              <p class="muted-copy">Nenhuma janela de trabalho nesta data.</p>
            }
          </div>
        }
      </article>

      <article class="surface-panel">
        <span class="panel-icon"><app-icon name="calendar" /></span>
        <h2>Folgas e jornadas especiais</h2>
        <form (ngSubmit)="criarExcecao()" #excecaoForm="ngForm">
          <div class="form-grid">
            <label>Data<input type="date" name="excecaoData" [(ngModel)]="excecaoData" required></label>
            <label>Tipo<select name="excecaoTipo" [(ngModel)]="excecaoTipo">
              <option value="FOLGA">Folga (dia inteiro)</option>
              <option value="JORNADA_ESPECIAL">Jornada especial</option>
            </select></label>
          </div>
          @if (excecaoTipo === 'JORNADA_ESPECIAL') {
            <div class="form-grid">
              <label>Início<input type="time" name="excecaoInicio" [(ngModel)]="excecaoInicio"></label>
              <label>Fim<input type="time" name="excecaoFim" [(ngModel)]="excecaoFim"></label>
            </div>
          }
          <label>Motivo (opcional)<input name="excecaoMotivo" [(ngModel)]="excecaoMotivo" maxlength="500"></label>
          <div class="form-actions"><button class="button primary" [disabled]="excecaoForm.invalid">Registrar</button></div>
        </form>
        <div class="agenda-list">
          @for (excecao of excecoes(); track excecao.id) {
            <article class="agenda-item">
              <div><h3>{{ formatarData(excecao.data) }} · {{ excecao.tipo === 'FOLGA' ? 'Folga' : 'Jornada especial' }}</h3>
                <p>{{ excecao.motivo || 'Sem motivo informado' }}</p></div>
              <button type="button" class="button ghost small" (click)="removerExcecao(excecao.id)">Remover</button>
            </article>
          } @empty { <p class="muted-copy">Nenhuma folga ou jornada especial cadastrada.</p> }
        </div>
      </article>

      <article class="surface-panel">
        <span class="panel-icon"><app-icon name="calendar" /></span>
        <h2>Férias e afastamentos</h2>
        <form (ngSubmit)="criarAfastamento()" #afastamentoForm="ngForm">
          <div class="form-grid">
            <label>Início<input type="date" name="afastamentoInicio" [(ngModel)]="afastamentoInicio" required></label>
            <label>Fim<input type="date" name="afastamentoFim" [(ngModel)]="afastamentoFim" required></label>
            <label>Tipo<select name="afastamentoTipo" [(ngModel)]="afastamentoTipo">
              <option value="FERIAS">Férias</option>
              <option value="LICENCA">Licença</option>
              <option value="AFASTAMENTO">Afastamento</option>
              <option value="OUTRO">Outro</option>
            </select></label>
          </div>
          <label>Descrição (opcional)<input name="afastamentoDescricao" [(ngModel)]="afastamentoDescricao" maxlength="500"></label>
          <div class="form-actions"><button class="button primary" [disabled]="afastamentoForm.invalid">Registrar</button></div>
        </form>
        <div class="agenda-list">
          @for (afastamento of afastamentos(); track afastamento.id) {
            <article class="agenda-item">
              <div><h3>{{ formatarData(afastamento.dataInicio) }} – {{ formatarData(afastamento.dataFim) }}</h3>
                <p>{{ rotuloAfastamento(afastamento.tipo) }}{{ afastamento.descricao ? ' · ' + afastamento.descricao : '' }}</p></div>
              <button type="button" class="button ghost small" (click)="removerAfastamento(afastamento.id)">Remover</button>
            </article>
          } @empty { <p class="muted-copy">Nenhum afastamento cadastrado.</p> }
        </div>
      </article>

      <article class="surface-panel">
        <span class="panel-icon"><app-icon name="clock" /></span>
        <h2>Bloqueios de agenda</h2>
        <form (ngSubmit)="criarBloqueio()" #bloqueioForm="ngForm">
          <div class="form-grid">
            <label>Data<input type="date" name="bloqueioData" [(ngModel)]="bloqueioData" required></label>
            <label class="checkbox-line" style="align-self: end;">Dia inteiro
              <input type="checkbox" name="bloqueioDiaInteiro" [(ngModel)]="bloqueioDiaInteiro"></label>
          </div>
          @if (!bloqueioDiaInteiro) {
            <div class="form-grid">
              <label>Início<input type="time" name="bloqueioInicio" [(ngModel)]="bloqueioInicio"></label>
              <label>Fim<input type="time" name="bloqueioFim" [(ngModel)]="bloqueioFim"></label>
            </div>
          }
          <label>Motivo (opcional)<input name="bloqueioMotivo" [(ngModel)]="bloqueioMotivo" maxlength="500"></label>
          <div class="form-actions"><button class="button primary" [disabled]="bloqueioForm.invalid">Bloquear</button></div>
        </form>
        <div class="agenda-list">
          @for (bloqueio of bloqueios(); track bloqueio.id) {
            <article class="agenda-item">
              <div><h3>{{ formatarData(bloqueio.data) }} · {{ bloqueio.diaInteiro ? 'Dia inteiro'
                : bloqueio.horaInicio!.slice(0, 5) + ' – ' + bloqueio.horaFim!.slice(0, 5) }}</h3>
                <p>{{ bloqueio.motivo || 'Sem motivo informado' }}</p></div>
              <button type="button" class="button ghost small" (click)="removerBloqueio(bloqueio.id)">Remover</button>
            </article>
          } @empty { <p class="muted-copy">Nenhum bloqueio cadastrado.</p> }
        </div>
      </article>
    </section>
  `
})
export class MinhaDisponibilidadeComponent implements OnInit {
  readonly formatarData = formatarData;
  private readonly svc = inject(DisponibilidadeService);
  private readonly auth = inject(AuthService);

  readonly dias = [
    { valor: 1, nome: 'Segunda-feira' }, { valor: 2, nome: 'Terça-feira' },
    { valor: 3, nome: 'Quarta-feira' }, { valor: 4, nome: 'Quinta-feira' },
    { valor: 5, nome: 'Sexta-feira' }, { valor: 6, nome: 'Sábado' },
    { valor: 7, nome: 'Domingo' }
  ];

  readonly intervalos = signal<JornadaIntervalo[]>([]);
  readonly janelas = signal<Janela[]>([]);
  readonly excecoes = signal<ExcecaoJornada[]>([]);
  readonly afastamentos = signal<Afastamento[]>([]);
  readonly bloqueios = signal<Bloqueio[]>([]);
  readonly erro = signal('');
  readonly sucesso = signal('');
  readonly salvando = signal(false);
  readonly previewFeito = signal(false);

  previewData = hojeIso();
  excecaoData = hojeIso(); excecaoTipo = 'FOLGA'; excecaoInicio = '09:00'; excecaoFim = '12:00';
  excecaoMotivo = '';
  afastamentoInicio = hojeIso(); afastamentoFim = somarDias(7); afastamentoTipo = 'FERIAS';
  afastamentoDescricao = '';
  bloqueioData = hojeIso(); bloqueioDiaInteiro = true; bloqueioInicio = '09:00'; bloqueioFim = '12:00';
  bloqueioMotivo = '';

  ngOnInit(): void {
    this.svc.minhaJornada().subscribe({
      next: jornada => this.intervalos.set(jornada.intervalos),
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
    this.carregarListas();
  }

  intervalosDoDia(dia: number): JornadaIntervalo[] {
    return this.intervalos().filter(intervalo => intervalo.diaSemana === dia);
  }

  adicionarIntervalo(dia: number): void {
    if (this.intervalosDoDia(dia).length >= 4) { this.erro.set('Um dia aceita no máximo quatro intervalos.'); return; }
    this.intervalos.update(lista => [...lista, { diaSemana: dia, horaInicio: '09:00', horaFim: '18:00' }]);
  }

  removerIntervalo(intervalo: JornadaIntervalo): void {
    this.intervalos.update(lista => lista.filter(item => item !== intervalo));
  }

  salvarJornada(): void {
    this.limparMensagens();
    this.salvando.set(true);
    const corpo: JornadaIntervalo[] = this.intervalos().map(intervalo => ({
      diaSemana: intervalo.diaSemana, horaInicio: this.hhmm(intervalo.horaInicio), horaFim: this.hhmm(intervalo.horaFim)
    }));
    this.svc.atualizarMinhaJornada(corpo).subscribe({
      next: jornada => { this.intervalos.set(jornada.intervalos); this.sucesso.set('Jornada atualizada.');
        this.salvando.set(false); },
      error: erro => { this.erro.set(AuthService.mensagemErro(erro)); this.salvando.set(false); }
    });
  }

  preverJanelas(): void {
    this.limparMensagens();
    this.previewFeito.set(true);
    this.svc.minhasJanelas(this.previewData).subscribe({
      next: janelas => this.janelas.set(janelas),
      error: erro => { this.janelas.set([]); this.erro.set(AuthService.mensagemErro(erro)); }
    });
  }

  criarExcecao(): void {
    this.limparMensagens();
    const corpo: { data: string; tipo: string; motivo?: string; intervalos?: { horaInicio: string; horaFim: string }[] } = {
      data: this.excecaoData, tipo: this.excecaoTipo, motivo: this.excecaoMotivo || undefined
    };
    if (this.excecaoTipo === 'JORNADA_ESPECIAL') {
      corpo.intervalos = [{ horaInicio: this.hhmm(this.excecaoInicio), horaFim: this.hhmm(this.excecaoFim) }];
    }
    this.svc.criarMinhaExcecao(corpo).subscribe({
      next: () => { this.sucesso.set('Exceção registrada.'); this.carregarListas(); },
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
  }

  removerExcecao(id: number): void {
    this.limparMensagens();
    this.svc.removerMinhaExcecao(id).subscribe({
      next: () => { this.sucesso.set('Exceção removida.'); this.carregarListas(); },
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
  }

  criarAfastamento(): void {
    this.limparMensagens();
    this.svc.criarMeuAfastamento({
      dataInicio: this.afastamentoInicio, dataFim: this.afastamentoFim,
      tipo: this.afastamentoTipo, descricao: this.afastamentoDescricao || undefined
    }).subscribe({
      next: () => { this.sucesso.set('Afastamento registrado.'); this.carregarListas(); },
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
  }

  removerAfastamento(id: number): void {
    this.limparMensagens();
    this.svc.removerMeuAfastamento(id).subscribe({
      next: () => { this.sucesso.set('Afastamento removido.'); this.carregarListas(); },
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
  }

  criarBloqueio(): void {
    this.limparMensagens();
    this.svc.criarMeuBloqueio({
      data: this.bloqueioData, diaInteiro: this.bloqueioDiaInteiro,
      horaInicio: this.bloqueioDiaInteiro ? null : this.hhmm(this.bloqueioInicio),
      horaFim: this.bloqueioDiaInteiro ? null : this.hhmm(this.bloqueioFim),
      motivo: this.bloqueioMotivo || undefined
    }).subscribe({
      next: () => { this.sucesso.set('Bloqueio registrado.'); this.carregarListas(); },
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
  }

  removerBloqueio(id: number): void {
    this.limparMensagens();
    this.svc.removerMeuBloqueio(id).subscribe({
      next: () => { this.sucesso.set('Bloqueio removido.'); this.carregarListas(); },
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
  }

  rotuloAfastamento(tipo: string): string {
    return { FERIAS: 'Férias', LICENCA: 'Licença', AFASTAMENTO: 'Afastamento', OUTRO: 'Outro' }[tipo] ?? tipo;
  }

  private carregarListas(): void {
    const de = hojeIso();
    const ate = somarDias(180);
    this.svc.minhasExcecoes(de, ate).subscribe({ next: itens => this.excecoes.set(itens) });
    this.svc.meusAfastamentos(de, ate).subscribe({ next: itens => this.afastamentos.set(itens) });
    const sessao = this.auth.sessao();
    if (sessao?.unidadeId != null && sessao.profissionalId != null) {
      this.svc.bloqueios(sessao.unidadeId, de, ate, sessao.profissionalId).subscribe({
        next: itens => this.bloqueios.set(itens.filter(item => item.profissionalId != null))
      });
    }
  }

  private limparMensagens(): void { this.erro.set(''); this.sucesso.set(''); }
  private hhmm(valor: string): string { return valor.length > 5 ? valor.slice(0, 5) : valor; }
}

@Component({
  standalone: true,
  imports: [FormsModule, UiIconComponent],
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Administração</p><h1>Feriados e bloqueios</h1>
        <p>Feriados fecham a filial inteira em uma data; bloqueios de filial removem uma janela de todos os profissionais.</p></div>

      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      @if (sucesso()) { <p class="notice success" role="status">{{ sucesso() }}</p> }

      <article class="surface-panel">
        <span class="panel-icon"><app-icon name="calendar" /></span>
        <h2>Feriados</h2>
        <form (ngSubmit)="criarFeriado()" #feriadoForm="ngForm">
          <div class="form-grid">
            <label>Data<input type="date" name="feriadoData" [(ngModel)]="feriadoData" required></label>
            <label>Nome<input name="feriadoNome" [(ngModel)]="feriadoNome" required maxlength="120"></label>
          </div>
          <div class="form-actions"><button class="button primary" [disabled]="feriadoForm.invalid">Registrar feriado</button></div>
        </form>
        <div class="agenda-list" syle="margin-top: 2rem !important;">
          @for (feriado of feriados(); track feriado.id) {
            <article class="agenda-item">
              <div><h3>{{ formatarData(feriado.data) }}</h3><p>{{ feriado.nome }}</p></div>
              <button type="button" class="button ghost small" style="align-self: end; width: max-content; min-width: 0;" (click)="removerFeriado(feriado.id)">Remover</button>
            </article>
          } @empty { <p class="muted-copy">Nenhum feriado cadastrado no período.</p> }
        </div>
      </article>

      <article class="surface-panel">
        <span class="panel-icon"><app-icon name="clock" /></span>
        <h2>Bloqueios de filial</h2>
        <form (ngSubmit)="criarBloqueio()" #bloqueioForm="ngForm">
          <div class="form-grid">
            <label>Data<input type="date" name="bloqueioData" [(ngModel)]="bloqueioData" required></label>
            <label class="checkbox-line" style="align-self: end;">Dia inteiro
              <input type="checkbox" name="bloqueioDiaInteiro" [(ngModel)]="bloqueioDiaInteiro"></label>
          </div>
          @if (!bloqueioDiaInteiro) {
            <div class="form-grid">
              <label>Início<input type="time" name="bloqueioInicio" [(ngModel)]="bloqueioInicio"></label>
              <label>Fim<input type="time" name="bloqueioFim" [(ngModel)]="bloqueioFim"></label>
            </div>
          }
          <label>Motivo (opcional)<input name="bloqueioMotivo" [(ngModel)]="bloqueioMotivo" maxlength="500"></label>
          <div class="form-actions"><button class="button primary" [disabled]="bloqueioForm.invalid">Bloquear filial</button></div>
        </form>
        <div class="agenda-list">
          @for (bloqueio of bloqueiosFilial(); track bloqueio.id) {
            <article class="agenda-item">
              <div><h3>{{ formatarData(bloqueio.data) }} · {{ bloqueio.diaInteiro ? 'Dia inteiro'
                : bloqueio.horaInicio!.slice(0, 5) + ' – ' + bloqueio.horaFim!.slice(0, 5) }}</h3>
                <p>{{ bloqueio.motivo || 'Sem motivo informado' }}</p></div>
              <button type="button" class="button ghost small" (click)="removerBloqueio(bloqueio.id)">Remover</button>
            </article>
          } @empty { <p class="muted-copy">Nenhum bloqueio de filial no período.</p> }
        </div>
      </article>
    </section>
  `
})
export class AdministracaoAgendaComponent implements OnInit {
  readonly formatarData = formatarData;
  private readonly svc = inject(DisponibilidadeService);
  private readonly auth = inject(AuthService);

  readonly feriados = signal<{ id: number; data: string; nome: string }[]>([]);
  readonly bloqueios = signal<Bloqueio[]>([]);
  readonly erro = signal('');
  readonly sucesso = signal('');

  feriadoData = hojeIso(); feriadoNome = '';
  bloqueioData = hojeIso(); bloqueioDiaInteiro = true; bloqueioInicio = '09:00'; bloqueioFim = '12:00';
  bloqueioMotivo = '';

  ngOnInit(): void { this.carregar(); }

  bloqueiosFilial(): Bloqueio[] {
    return this.bloqueios().filter(bloqueio => bloqueio.profissionalId == null);
  }

  criarFeriado(): void {
    this.limpar();
    const unidadeId = this.unidadeId();
    if (unidadeId == null) { return; }
    this.svc.criarFeriado(unidadeId, this.feriadoData, this.feriadoNome).subscribe({
      next: () => { this.sucesso.set('Feriado registrado.'); this.feriadoNome = ''; this.carregar(); },
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
  }

  removerFeriado(id: number): void {
    this.limpar();
    const unidadeId = this.unidadeId();
    if (unidadeId == null) { return; }
    this.svc.removerFeriado(unidadeId, id).subscribe({
      next: () => { this.sucesso.set('Feriado removido.'); this.carregar(); },
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
  }

  criarBloqueio(): void {
    this.limpar();
    const unidadeId = this.unidadeId();
    if (unidadeId == null) { return; }
    this.svc.criarBloqueio(unidadeId, {
      data: this.bloqueioData, diaInteiro: this.bloqueioDiaInteiro,
      horaInicio: this.bloqueioDiaInteiro ? null : this.hhmm(this.bloqueioInicio),
      horaFim: this.bloqueioDiaInteiro ? null : this.hhmm(this.bloqueioFim),
      motivo: this.bloqueioMotivo || undefined
    }).subscribe({
      next: () => { this.sucesso.set('Bloqueio registrado.'); this.carregar(); },
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
  }

  removerBloqueio(id: number): void {
    this.limpar();
    const unidadeId = this.unidadeId();
    if (unidadeId == null) { return; }
    this.svc.removerBloqueio(unidadeId, id).subscribe({
      next: () => { this.sucesso.set('Bloqueio removido.'); this.carregar(); },
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
  }

  private carregar(): void {
    const unidadeId = this.unidadeId();
    if (unidadeId == null) { return; }
    const de = hojeIso();
    const ate = somarDias(365);
    this.svc.feriados(unidadeId, de, ate).subscribe({
      next: itens => this.feriados.set(itens),
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
    this.svc.bloqueios(unidadeId, de, ate).subscribe({
      next: itens => this.bloqueios.set(itens),
      error: erro => this.erro.set(AuthService.mensagemErro(erro))
    });
  }

  private unidadeId(): number | null { return this.auth.sessao()?.unidadeId ?? null; }
  private limpar(): void { this.erro.set(''); this.sucesso.set(''); }
  private hhmm(valor: string): string { return valor.length > 5 ? valor.slice(0, 5) : valor; }
}
