import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
import { ConsultaHorarios, Estabelecimento, EstabelecimentoService, Filial, FilialDados,
  HorarioDisponivel, Profissional, ServicoPublico } from './estabelecimento.service';
import { UiIconComponent } from './ui-icon.component';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, UiIconComponent],
  template: `
    <section class="page-stack">
      <div class="page-heading"><p class="eyebrow">Administração</p><h1>Seu espaço de gestão</h1>
        <p>Organize sua filial, equipe e profissionais em um só lugar.</p></div>
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      @if (mensagem()) { <p class="notice success" role="status">{{ mensagem() }}</p> }
      @if (filial(); as atual) {
        <div class="overview-grid">
          <article class="surface-panel summary-card"><span class="panel-icon"><app-icon name="building" /></span>
            <p class="eyebrow">Minha filial</p><h2>{{ atual.nome }}</h2>
            <span class="badge">{{ atual.ativa ? 'Ativa' : 'Inativa' }}</span>
            @if (atual.ativa) { <a class="text-link" [routerLink]="['/unidades', atual.id]">Ver página pública <app-icon name="arrow" /></a> }
          </article>
          @if (estabelecimento(); as empresa) {
            <article class="surface-panel summary-card"><span class="panel-icon"><app-icon name="sparkles" /></span>
              <p class="eyebrow">Estabelecimento</p><h2>{{ empresa.nome }}</h2>
              <span class="muted-copy">{{ empresa.administradorPrincipal ? 'Filial principal' : 'Sua organização' }}</span>
            </article>
          }
        </div>
        <section id="filial" class="surface-panel section-card">
          <div class="section-title"><div><p class="eyebrow">Configuração</p><h2>Dados da filial</h2></div><app-icon name="building" /></div>
          <p class="muted-copy">Atualize as informações apresentadas aos clientes da sua filial.</p>
          <form (ngSubmit)="salvarFilial()" #formFilial="ngForm">
            <div class="form-grid">
              <label>Nome<input name="filialNome" [(ngModel)]="filialDados.nome" required minlength="2" maxlength="120"></label>
              <label>Telefone<input name="telefone" [(ngModel)]="filialDados.telefone" maxlength="30"></label>
              <label>Endereço<input name="endereco" [(ngModel)]="filialDados.endereco" maxlength="250"></label>
              <label>Fuso horário IANA<input name="fuso" [(ngModel)]="filialDados.fusoHorario" required></label>
            </div>
            <label class="checkbox-line"><input type="checkbox" name="ativa" [(ngModel)]="filialDados.ativa">Filial ativa</label>
            <div class="form-actions"><button class="button primary" [disabled]="formFilial.invalid || salvando()">Salvar filial</button></div>
          </form>
        </section>
      }
      @if (estabelecimento(); as atual) {
        <section id="estabelecimento" class="surface-panel section-card">
          <div class="section-title"><div><p class="eyebrow">Organização</p><h2>Estabelecimento</h2></div><app-icon name="sparkles" /></div>
          <p class="muted-copy">{{ atual.nome }}</p>
        @if (atual.administradorPrincipal) {
          <form (ngSubmit)="salvarEstabelecimento()" #formEstabelecimento="ngForm">
            <label>Nome do estabelecimento<input name="estabelecimentoNome" [(ngModel)]="nomeEstabelecimento" required minlength="2" maxlength="120"></label>
            <div class="form-actions"><button class="button ghost" [disabled]="formEstabelecimento.invalid || salvando()">Salvar nome</button></div>
          </form>
        }
        </section>
        @if (atual.administradorPrincipal) {
          <section id="filiais" class="surface-panel section-card">
          <div class="section-title"><div><p class="eyebrow">Organização</p><h2>Filiais</h2></div><app-icon name="building" /></div>
          <div class="user-list">
            @for (item of atual.filiais; track item.id) {
              <article><strong>{{ item.nome }}</strong><span class="status">{{ item.ativa ? 'Ativa' : 'Inativa' }}</span></article>
            }
          </div>
          <details class="create-details"><summary><app-icon name="sparkles" /> Criar filial</summary>
          <form (ngSubmit)="criarFilial()" #formNovaFilial="ngForm">
            <div class="form-grid">
              <label>Nome da filial<input name="novaFilialNome" [(ngModel)]="novaFilial.nome" required minlength="2" maxlength="120"></label>
              <label>Telefone<input name="novoTelefone" [(ngModel)]="novaFilial.telefone" maxlength="30"></label>
              <label>Endereço<input name="novoEndereco" [(ngModel)]="novaFilial.endereco" maxlength="250"></label>
              <label>Fuso horário IANA<input name="novoFuso" [(ngModel)]="novaFilial.fusoHorario" required></label>
              <label>Nome do primeiro administrador<input name="adminNome" [(ngModel)]="adminNome" required minlength="2" maxlength="120"></label>
              <label>E-mail do primeiro administrador<input type="email" name="adminEmail" [(ngModel)]="adminEmail" required email></label>
            </div>
            <button class="button primary" [disabled]="formNovaFilial.invalid || salvando()">Criar filial e enviar convite</button>
          </form>
          </details>
          </section>
        }
      }
      @if (filial()) {
        <section id="profissionais" class="surface-panel section-card">
        <div class="section-title"><div><p class="eyebrow">Equipe de atendimento</p><h2>Profissionais</h2></div><app-icon name="users" /></div>
        <p class="muted-copy">Gerencie quem atende na sua filial.</p>
        <details class="create-details"><summary><app-icon name="user" /> Adicionar profissional</summary>
        <form (ngSubmit)="criarProfissional()" #formProfissional="ngForm">
          <label>Nome<input name="profNome" [(ngModel)]="profNome" required minlength="2" maxlength="120"></label>
          <label>Apresentação<textarea name="profApresentacao" [(ngModel)]="profApresentacao" maxlength="500"></textarea></label>
          <button class="button primary" [disabled]="formProfissional.invalid || salvando()">Adicionar profissional</button>
        </form>
        </details>
        <div class="user-list">
          @for (profissional of profissionais(); track profissional.id) {
            <article>
              <div><strong>{{ profissional.nome }}</strong><small>{{ profissional.apresentacao }}</small></div>
              <span class="status">{{ profissional.ativo ? 'Ativo' : 'Inativo' }}</span>
              <button class="button ghost small" type="button" (click)="alternarProfissional(profissional)" [disabled]="salvando()">
                {{ profissional.ativo ? 'Desativar' : 'Ativar' }}
              </button>
              <button class="button ghost small" type="button" (click)="editarProfissional(profissional)" [disabled]="salvando()">Editar</button>
            </article>
          }
        </div>
        @if (editando(); as profissional) {
          <form class="edit-panel" (ngSubmit)="salvarEdicao()" #formEdicao="ngForm">
            <h3>Editar {{ profissional.nome }}</h3>
            <label>Nome<input name="edicaoNome" [(ngModel)]="edicaoNome" required minlength="2" maxlength="120"></label>
            <label>Apresentação<textarea name="edicaoApresentacao" [(ngModel)]="edicaoApresentacao" maxlength="500"></textarea></label>
            <div class="form-actions"><button class="button primary" [disabled]="formEdicao.invalid || salvando()">Salvar alterações</button>
              <button class="button ghost" type="button" (click)="editando.set(null)">Cancelar</button></div>
          </form>
        }
        </section>
      }
    </section>
  `
})
export class EstabelecimentoAdminComponent implements OnInit {
  private readonly api = inject(EstabelecimentoService);
  private readonly auth = inject(AuthService);
  readonly filial = signal<Filial | null>(null);
  readonly estabelecimento = signal<Estabelecimento | null>(null);
  readonly profissionais = signal<Profissional[]>([]);
  readonly editando = signal<Profissional | null>(null);
  readonly erro = signal(''); readonly mensagem = signal(''); readonly salvando = signal(false);
  filialDados: FilialDados = { nome: '', endereco: '', telefone: '', fusoHorario: 'America/Sao_Paulo' };
  novaFilial: FilialDados = { nome: '', endereco: '', telefone: '', fusoHorario: 'America/Sao_Paulo' };
  nomeEstabelecimento = ''; adminNome = ''; adminEmail = ''; profNome = ''; profApresentacao = '';
  edicaoNome = ''; edicaoApresentacao = '';

  ngOnInit(): void { this.carregar(); }

  private carregar(): void {
    const unidadeId = this.auth.sessao()?.unidadeId;
    if (unidadeId == null) { this.erro.set('Sua conta não possui filial.'); return; }
    this.api.filial(unidadeId).subscribe({
      next: (filial) => {
        this.filial.set(filial);
        this.filialDados = { nome: filial.nome, endereco: filial.endereco ?? '',
          telefone: filial.telefone ?? '', fusoHorario: filial.fusoHorario, ativa: filial.ativa };
        this.api.estabelecimento(filial.estabelecimentoId).subscribe({
          next: (estabelecimento) => {
            this.estabelecimento.set(estabelecimento);
            this.nomeEstabelecimento = estabelecimento.nome;
          }, error: (e) => this.erro.set(AuthService.mensagemErro(e))
        });
        this.recarregarProfissionais(unidadeId);
      }, error: (e) => this.erro.set(AuthService.mensagemErro(e))
    });
  }

  salvarFilial(): void {
    const filial = this.filial(); if (!filial) { return; }
    this.enviar(() => this.api.atualizarFilial(filial.id, this.filialDados), (nova) => {
      this.filial.set(nova); this.mensagem.set('Filial atualizada.');
    });
  }

  salvarEstabelecimento(): void {
    const atual = this.estabelecimento(); if (!atual) { return; }
    this.enviar(() => this.api.atualizarEstabelecimento(atual.id, this.nomeEstabelecimento), (novo) => {
      this.estabelecimento.set(novo); this.mensagem.set('Estabelecimento atualizado.');
    });
  }

  criarFilial(): void {
    const atual = this.estabelecimento(); if (!atual) { return; }
    this.enviar(() => this.api.criarFilial(atual.id, this.novaFilial, this.adminNome, this.adminEmail), () => {
      this.novaFilial = { nome: '', endereco: '', telefone: '', fusoHorario: 'America/Sao_Paulo' };
      this.adminNome = ''; this.adminEmail = '';
      this.mensagem.set('Filial criada. O convite do administrador foi enviado.');
      this.api.estabelecimento(atual.id).subscribe((valor) => this.estabelecimento.set(valor));
    });
  }

  criarProfissional(): void {
    const filial = this.filial(); if (!filial) { return; }
    this.enviar(() => this.api.criarProfissional(filial.id, this.profNome, this.profApresentacao), () => {
      this.profNome = ''; this.profApresentacao = '';
      this.mensagem.set('Profissional cadastrado.'); this.recarregarProfissionais(filial.id);
    });
  }

  alternarProfissional(profissional: Profissional): void {
    const filial = this.filial(); if (!filial) { return; }
    this.enviar(() => this.api.atualizarProfissional(filial.id,
      { ...profissional, ativo: !profissional.ativo }), () => {
      this.mensagem.set('Estado do profissional atualizado.'); this.recarregarProfissionais(filial.id);
    });
  }

  editarProfissional(profissional: Profissional): void {
    this.editando.set(profissional);
    this.edicaoNome = profissional.nome;
    this.edicaoApresentacao = profissional.apresentacao ?? '';
  }

  salvarEdicao(): void {
    const filial = this.filial(); const profissional = this.editando();
    if (!filial || !profissional) { return; }
    this.enviar(() => this.api.atualizarProfissional(filial.id,
      { ...profissional, nome: this.edicaoNome, apresentacao: this.edicaoApresentacao }), () => {
      this.editando.set(null); this.mensagem.set('Profissional atualizado.');
      this.recarregarProfissionais(filial.id);
    });
  }

  private recarregarProfissionais(id: number): void {
    this.api.profissionais(id, true).subscribe({
      next: (lista) => this.profissionais.set(lista),
      error: (e) => this.erro.set(AuthService.mensagemErro(e))
    });
  }

  private enviar<T>(acao: () => import('rxjs').Observable<T>, sucesso: (valor: T) => void): void {
    this.erro.set(''); this.mensagem.set(''); this.salvando.set(true);
    acao().subscribe({
      next: sucesso,
      error: (e) => { this.erro.set(AuthService.mensagemErro(e)); this.salvando.set(false); },
      complete: () => this.salvando.set(false)
    });
  }
}

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, UiIconComponent],
  template: `
    <section class="content-card wide public-branch">
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      @if (filial(); as atual) {
        <p class="eyebrow">Filial</p><h1>{{ atual.nome }}</h1>
        @if (atual.endereco) { <p>{{ atual.endereco }}</p> }
        @if (atual.telefone) { <p>Telefone: {{ atual.telefone }}</p> }
        <section class="surface-panel section-card">
          <div class="section-title"><div><p class="eyebrow">Agenda</p><h2>Encontre um horário</h2></div>
            <app-icon name="calendar" /></div>
          <p class="muted-copy">Horários exibidos no fuso {{ atual.fusoHorario }}. A escolha ainda não é uma reserva.</p>
          @if (servicos().length) {
            <div class="form-grid">
              <label>Serviço
                <select name="servico" [(ngModel)]="servicoSelecionado" (ngModelChange)="trocarServico()">
                  @for (servico of servicos(); track servico.id) {
                    <option [ngValue]="servico.id">{{ servico.nome }} · {{ servico.duracaoMinutos }} min</option>
                  }
                </select>
              </label>
              <label>Data
                <input type="date" name="data" [(ngModel)]="dataSelecionada" [min]="dataMinima()"
                  [max]="dataMaxima()" (change)="consultarHorarios()">
              </label>
              <label>Profissional
                <select name="profissional" [(ngModel)]="profissionalSelecionado"
                  (ngModelChange)="consultarHorarios()">
                  <option [ngValue]="null">Qualquer profissional</option>
                  @for (profissional of profissionaisDoServico(); track profissional.id) {
                    <option [ngValue]="profissional.id">{{ profissional.nome }}</option>
                  }
                </select>
              </label>
            </div>
            <div class="form-actions"><button class="button ghost" type="button"
              (click)="consultarHorarios()" [disabled]="carregandoHorarios()">Atualizar horários</button></div>
            @if (erroHorarios()) { <p class="notice error" role="alert">{{ erroHorarios() }}</p> }
            @if (carregandoHorarios()) { <p role="status">Consultando horários…</p> }
            @else if (consulta(); as resultado) {
              @if (resultado.horarios.length) {
                <div class="slot-grid" aria-label="Horários disponíveis">
                  @for (horario of resultado.horarios; track horario.inicio + '-' + horario.profissionalId) {
                    <button type="button" class="slot-option" [class.selected]="selecionado() === horario"
                      (click)="selecionado.set(horario)"
                      [attr.aria-pressed]="selecionado() === horario">
                      <strong>{{ hora(horario.inicio) }}–{{ hora(horario.fim) }}</strong>
                      <small>{{ nomeProfissional(horario.profissionalId) }}</small>
                    </button>
                  }
                </div>
                @if (selecionado(); as escolhido) {
                  <p class="notice success" role="status">Selecionado: {{ hora(escolhido.inicio) }} com
                    {{ nomeProfissional(escolhido.profissionalId) }}. Revise os dados antes de reservar.</p>
                  <a class="button primary" [routerLink]="['/unidades', atual.id, 'revisar']"
                    [queryParams]="{ servicoId: servicoSelecionado, profissionalId: escolhido.profissionalId,
                      inicio: escolhido.inicio }">Revisar agendamento</a>
                }
              } @else {
                <p class="muted-copy" role="status">Não há horários livres nessa data. Tente outro dia ou profissional.</p>
              }
            }
          } @else {
            <p class="muted-copy">Esta filial ainda não possui serviços com profissionais habilitados.</p>
          }
        </section>
        <section class="surface-panel section-card"><h2>Profissionais</h2>
          <div class="user-list">@for (profissional of profissionais(); track profissional.id) {
            <article><div><strong>{{ profissional.nome }}</strong><small>{{ profissional.apresentacao }}</small></div></article>
          }</div>
        </section>
      }
      <a routerLink="/">Voltar</a>
    </section>
  `
})
export class FilialPublicaComponent implements OnInit {
  private readonly api = inject(EstabelecimentoService);
  private readonly route = inject(ActivatedRoute);
  readonly filial = signal<Filial | null>(null);
  readonly profissionais = signal<Profissional[]>([]);
  readonly servicos = signal<ServicoPublico[]>([]);
  readonly consulta = signal<ConsultaHorarios | null>(null);
  readonly selecionado = signal<HorarioDisponivel | null>(null);
  readonly carregandoHorarios = signal(false);
  readonly erroHorarios = signal('');
  readonly erro = signal('');
  servicoSelecionado: number | null = null;
  profissionalSelecionado: number | null = null;
  dataSelecionada = '';
  private sequenciaConsulta = 0;

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id) || id <= 0) { this.erro.set('Filial não encontrada.'); return; }
    this.api.filialPublica(id).subscribe({
      next: (filial) => {
        this.filial.set(filial);
        this.dataSelecionada = this.dataLocal(filial.fusoHorario);
        this.api.profissionais(id).subscribe((lista) => this.profissionais.set(lista));
        this.api.servicosDisponiveis(id).subscribe({
          next: lista => {
            this.servicos.set(lista);
            const desejado = Number(this.route.snapshot.queryParamMap.get('servicoId'));
            this.servicoSelecionado = lista.find(item => item.id === desejado)?.id
              ?? lista[0]?.id ?? null;
            this.consultarHorarios();
          },
          error: () => this.erroHorarios.set('Não foi possível carregar os serviços.')
        });
      }, error: () => this.erro.set('Filial não encontrada.')
    });
  }

  profissionaisDoServico(): { id: number; nome: string }[] {
    return this.servicos().find(item => item.id === this.servicoSelecionado)?.profissionais ?? [];
  }

  trocarServico(): void {
    this.profissionalSelecionado = null;
    this.consultarHorarios();
  }

  consultarHorarios(): void {
    const filial = this.filial();
    if (!filial || this.servicoSelecionado == null || !this.dataSelecionada) { return; }
    const sequencia = ++this.sequenciaConsulta;
    this.selecionado.set(null);
    this.consulta.set(null);
    this.erroHorarios.set('');
    this.carregandoHorarios.set(true);
    this.api.horarios(filial.id, this.servicoSelecionado, this.dataSelecionada,
      this.profissionalSelecionado ?? undefined).subscribe({
      next: resultado => {
        if (sequencia !== this.sequenciaConsulta) { return; }
        this.consulta.set(resultado);
        this.carregandoHorarios.set(false);
      },
      error: erro => {
        if (sequencia !== this.sequenciaConsulta) { return; }
        this.erroHorarios.set(AuthService.mensagemErro(erro));
        this.carregandoHorarios.set(false);
      }
    });
  }

  dataMinima(): string { return this.dataLocal(this.filial()?.fusoHorario ?? 'America/Sao_Paulo'); }

  dataMaxima(): string {
    const inicio = new Date(`${this.dataMinima()}T12:00:00Z`);
    inicio.setUTCDate(inicio.getUTCDate() + 60);
    return inicio.toISOString().slice(0, 10);
  }

  hora(valor: string): string { return valor.slice(11, 16); }

  nomeProfissional(id: number): string {
    return this.profissionaisDoServico().find(item => item.id === id)?.nome ?? `Profissional ${id}`;
  }

  private dataLocal(fuso: string): string {
    const partes = new Intl.DateTimeFormat('en-US', { timeZone: fuso,
      year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
    const valor = (tipo: string) => partes.find(item => item.type === tipo)?.value ?? '';
    return `${valor('year')}-${valor('month')}-${valor('day')}`;
  }
}
