import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
import { Estabelecimento, EstabelecimentoService, Filial, FilialDados, Profissional } from './estabelecimento.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <section class="content-card wide">
      <p class="eyebrow">Administração</p><h1>Estabelecimento e filial</h1>
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      @if (mensagem()) { <p class="notice success" role="status">{{ mensagem() }}</p> }
      @if (filial(); as atual) {
        <p>Você administra a filial <strong>{{ atual.nome }}</strong>.</p>
        <a [routerLink]="['/unidades', atual.id]">Ver página pública</a>
        <h2>Dados da filial</h2>
        <form (ngSubmit)="salvarFilial()" #formFilial="ngForm">
          <label>Nome<input name="filialNome" [(ngModel)]="filialDados.nome" required minlength="2" maxlength="120"></label>
          <label>Endereço<input name="endereco" [(ngModel)]="filialDados.endereco" maxlength="250"></label>
          <label>Telefone<input name="telefone" [(ngModel)]="filialDados.telefone" maxlength="30"></label>
          <label>Fuso horário IANA<input name="fuso" [(ngModel)]="filialDados.fusoHorario" required></label>
          <label class="checkbox-line"><input type="checkbox" name="ativa" [(ngModel)]="filialDados.ativa">Filial ativa</label>
          <button class="button primary" [disabled]="formFilial.invalid || salvando()">Salvar filial</button>
        </form>
      }
      @if (estabelecimento(); as atual) {
        <h2>Estabelecimento</h2><p>{{ atual.nome }}</p>
        @if (atual.administradorPrincipal) {
          <form (ngSubmit)="salvarEstabelecimento()" #formEstabelecimento="ngForm">
            <label>Nome do estabelecimento<input name="estabelecimentoNome" [(ngModel)]="nomeEstabelecimento" required minlength="2" maxlength="120"></label>
            <button class="button ghost" [disabled]="formEstabelecimento.invalid || salvando()">Salvar nome</button>
          </form>
          <h2>Filiais</h2>
          <div class="user-list">
            @for (item of atual.filiais; track item.id) {
              <article><strong>{{ item.nome }}</strong><span class="status">{{ item.ativa ? 'Ativa' : 'Inativa' }}</span></article>
            }
          </div>
          <h3>Criar filial</h3>
          <form (ngSubmit)="criarFilial()" #formNovaFilial="ngForm">
            <label>Nome da filial<input name="novaFilialNome" [(ngModel)]="novaFilial.nome" required minlength="2" maxlength="120"></label>
            <label>Endereço<input name="novoEndereco" [(ngModel)]="novaFilial.endereco" maxlength="250"></label>
            <label>Telefone<input name="novoTelefone" [(ngModel)]="novaFilial.telefone" maxlength="30"></label>
            <label>Fuso horário IANA<input name="novoFuso" [(ngModel)]="novaFilial.fusoHorario" required></label>
            <label>Nome do primeiro administrador<input name="adminNome" [(ngModel)]="adminNome" required minlength="2" maxlength="120"></label>
            <label>E-mail do primeiro administrador<input type="email" name="adminEmail" [(ngModel)]="adminEmail" required email></label>
            <button class="button primary" [disabled]="formNovaFilial.invalid || salvando()">Criar filial e enviar convite</button>
          </form>
        }
      }
      @if (filial()) {
        <h2>Profissionais</h2>
        <form (ngSubmit)="criarProfissional()" #formProfissional="ngForm">
          <label>Nome<input name="profNome" [(ngModel)]="profNome" required minlength="2" maxlength="120"></label>
          <label>Apresentação<textarea name="profApresentacao" [(ngModel)]="profApresentacao" maxlength="500"></textarea></label>
          <button class="button primary" [disabled]="formProfissional.invalid || salvando()">Adicionar profissional</button>
        </form>
        <div class="user-list">
          @for (profissional of profissionais(); track profissional.id) {
            <article>
              <div><strong>{{ profissional.nome }}</strong><small>{{ profissional.apresentacao }}</small></div>
              <span class="status">{{ profissional.ativo ? 'Ativo' : 'Inativo' }}</span>
              <button class="button ghost" (click)="alternarProfissional(profissional)" [disabled]="salvando()">
                {{ profissional.ativo ? 'Desativar' : 'Ativar' }}
              </button>
              <button class="button ghost" (click)="editarProfissional(profissional)" [disabled]="salvando()">Editar</button>
            </article>
          }
        </div>
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
  readonly erro = signal(''); readonly mensagem = signal(''); readonly salvando = signal(false);
  filialDados: FilialDados = { nome: '', endereco: '', telefone: '', fusoHorario: 'America/Sao_Paulo' };
  novaFilial: FilialDados = { nome: '', endereco: '', telefone: '', fusoHorario: 'America/Sao_Paulo' };
  nomeEstabelecimento = ''; adminNome = ''; adminEmail = ''; profNome = ''; profApresentacao = '';

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
    const filial = this.filial(); if (!filial) { return; }
    const nome = window.prompt('Nome do profissional', profissional.nome);
    if (nome === null) { return; }
    const apresentacao = window.prompt('Apresentação pública', profissional.apresentacao ?? '');
    if (apresentacao === null) { return; }
    this.enviar(() => this.api.atualizarProfissional(filial.id,
      { ...profissional, nome, apresentacao }), () => {
      this.mensagem.set('Profissional atualizado.'); this.recarregarProfissionais(filial.id);
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
  imports: [RouterLink],
  template: `
    <section class="content-card wide">
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      @if (filial(); as atual) {
        <p class="eyebrow">Filial</p><h1>{{ atual.nome }}</h1>
        @if (atual.endereco) { <p>{{ atual.endereco }}</p> }
        @if (atual.telefone) { <p>Telefone: {{ atual.telefone }}</p> }
        <h2>Profissionais</h2>
        <div class="user-list">@for (profissional of profissionais(); track profissional.id) {
          <article><div><strong>{{ profissional.nome }}</strong><small>{{ profissional.apresentacao }}</small></div></article>
        }</div>
        <h2>Serviços disponíveis</h2>
        <div class="user-list">@for (servico of servicos(); track servico.id) {
          <article><strong>{{ servico.nome }}</strong></article>
        }</div>
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
  readonly servicos = signal<{ id: number; nome: string }[]>([]);
  readonly erro = signal('');

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id) || id <= 0) { this.erro.set('Filial não encontrada.'); return; }
    this.api.filialPublica(id).subscribe({
      next: (filial) => {
        this.filial.set(filial);
        this.api.profissionais(id).subscribe((lista) => this.profissionais.set(lista));
        this.api.servicosDisponiveis(id).subscribe((lista) => this.servicos.set(lista));
      }, error: () => this.erro.set('Filial não encontrada.')
    });
  }
}
