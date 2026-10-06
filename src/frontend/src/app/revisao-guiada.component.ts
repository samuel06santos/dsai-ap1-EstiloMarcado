import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Agendamento, AgendamentoApi } from './agendamento.service';
import { chaveIdempotencia, lerRascunho, limparChaveIdempotencia,
  RascunhoAgendamento, retornoRevisaoSeguro } from './agendamento-rascunho';
import { AuthService } from './auth.service';
import { EstabelecimentoService, Filial, ServicoPublico } from './estabelecimento.service';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink],
  styleUrl: './jornada-guiada.css',
  template: `
    <section class="booking-shell booking-review">
      <header class="booking-heading">
        <p class="eyebrow">Passo 4 de 4 · Revisão</p>
        <h1>Confira seu agendamento</h1>
        <p>A vaga será revalidada quando você confirmar. Esta escolha não é uma reserva temporária.</p>
      </header>
      <nav aria-label="Progresso do agendamento" class="booking-progress">
        <ol>
          <li class="is-complete"><span>1</span>Serviço</li>
          <li class="is-complete"><span>2</span>Profissional</li>
          <li class="is-complete"><span>3</span>Dia e horário</li>
          <li class="is-current" aria-current="step"><span>4</span>Revisão</li>
        </ol>
      </nav>
      @if (sucesso(); as reservado) {
        <article class="booking-receipt" role="status">
          <p class="eyebrow">Reserva concluída</p>
          <h2>Agendamento #{{ reservado.id }}</h2>
          <p>Status: <strong>{{ reservado.status }}</strong>.</p>
          <p>{{ reservado.servicoNome }} com {{ reservado.profissionalNome ?? nomeProfissional() }}
            em {{ reservado.inicio.slice(0, 16).replace('T', ' às ') }}
            ({{ reservado.fusoHorario }}).</p>
          <p>Filial: {{ reservado.unidadeNome ?? filial()?.nome }} · Valor acordado:
            {{ reservado.precoAcordado | currency:'BRL' }}</p>
          <div class="booking-actions">
            <a class="button primary" routerLink="/meus-agendamentos"
              [queryParams]="{ mes: reservado.inicio.slice(0, 7), id: reservado.id }">
              Ver meus agendamentos</a>
            <a class="button ghost" [routerLink]="['/unidades', reservado.unidadeId]">Voltar à filial</a>
          </div>
        </article>
      } @else {
        @if (erro()) {
          <div class="notice error" role="alert">
            <p>{{ erro() }}</p>
            @if (rascunho() && filial()) {
              <a [routerLink]="['/unidades', rascunho()!.unidadeId]"
                [queryParams]="parametrosVoltar(erroServico() ? 1 : 3)">
                {{ erroServico() ? 'Escolher outro serviço' : 'Ver outros horários' }}</a>
              @if (erroRede()) {
                <button class="button ghost" type="button" (click)="revalidar()">Tentar novamente</button>
              }
            } @else {
              @if (erroRede()) {
                <button class="button ghost" type="button" (click)="revalidar()">Tentar novamente</button>
              }
              <a routerLink="/filiais">Encontrar uma filial</a>
            }
          </div>
        }
        @if (carregando()) { <p role="status">Conferindo sua escolha…</p> }
        @else if (filial() && servico() && horarioValido()) {
          <article class="booking-receipt">
            <h2>Seu horário</h2>
            <dl>
              <div><dt>Filial</dt><dd>{{ filial()!.nome }}</dd>
                <dd><a routerLink="/filiais">Alterar</a></dd></div>
              <div><dt>Serviço</dt><dd>{{ servico()!.nome }}</dd>
                <dd><a [routerLink]="['/unidades', filial()!.id]" [queryParams]="parametrosVoltar(1)">Alterar</a></dd></div>
              <div><dt>Profissional</dt><dd>{{ nomeProfissional() }}</dd>
                <dd><a [routerLink]="['/unidades', filial()!.id]" [queryParams]="parametrosVoltar(2)">Alterar</a></dd></div>
              <div><dt>Dia e horário</dt><dd>{{ rascunho()!.inicio.slice(0, 16).replace('T', ' às ') }}</dd>
                <dd><a [routerLink]="['/unidades', filial()!.id]" [queryParams]="parametrosVoltar(3)">Alterar</a></dd></div>
              <div><dt>Fuso horário</dt><dd>{{ filial()!.fusoHorario }}</dd></div>
              <div><dt>Duração</dt><dd>{{ servico()!.duracaoMinutos }} minutos</dd></div>
              <div><dt>Preço atual</dt><dd>{{ servico()!.preco | currency:'BRL' }}</dd></div>
              <div><dt>Estado após criar</dt><dd>AGENDADO</dd></div>
            </dl>
            @if (auth.sessao()?.perfil === 'CLIENTE') {
              <div class="booking-actions">
                <a class="button ghost" [routerLink]="['/unidades', filial()!.id]"
                  [queryParams]="parametrosVoltar(3)">Voltar aos horários</a>
                <button class="button primary" type="button" [disabled]="enviando()"
                  (click)="confirmar()">{{ enviando() ? 'Confirmando…' : 'Confirmar agendamento' }}</button>
              </div>
            } @else if (auth.sessao() === null) {
              <p>Entre ou crie uma conta para confirmar. Suas escolhas serão conferidas novamente após o acesso.</p>
              <div class="booking-actions">
                <a class="button primary" routerLink="/entrar" [queryParams]="{ retorno: retorno() }">Entrar para continuar</a>
                <a class="button ghost" routerLink="/cadastro" [queryParams]="{ retorno: retorno() }">Criar conta</a>
              </div>
            } @else if (auth.sessao()) {
              <p class="notice">Entre com uma conta de cliente para reservar.</p>
            } @else { <p role="status">Conferindo acesso…</p> }
          </article>
        }
      }
    </section>
  `
})
export class RevisaoAgendamentoComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly estabelecimentos = inject(EstabelecimentoService);
  private readonly agendamentos = inject(AgendamentoApi);
  readonly auth = inject(AuthService);
  readonly rascunho = signal<RascunhoAgendamento | null>(null);
  readonly filial = signal<Filial | null>(null);
  readonly servico = signal<ServicoPublico | null>(null);
  readonly horarioValido = signal(false);
  readonly carregando = signal(false);
  readonly enviando = signal(false);
  readonly erro = signal('');
  readonly erroServico = signal(false);
  readonly erroRede = signal(false);
  readonly sucesso = signal<Agendamento | null>(null);
  private chaveReserva = '';

  ngOnInit(): void {
    const rascunho = lerRascunho(this.route.snapshot.paramMap.get('id'), this.route.snapshot.queryParamMap);
    if (!rascunho) {
      this.erro.set('Sua escolha expirou ou o link é inválido. Escolha um novo horário para continuar.');
      return;
    }
    this.rascunho.set(rascunho);
    this.chaveReserva = chaveIdempotencia(rascunho);
    this.revalidar();
  }

  retorno(): string { return retornoRevisaoSeguro(this.router.url) ?? ''; }

  parametrosVoltar(passo: 1 | 2 | 3): Record<string, string | number | null> {
    const atual = this.rascunho();
    if (!atual) return {};
    return { passo, servicoId: atual.servicoId, preferenciaId: atual.preferenciaId,
      qualquer: atual.preferenciaId === null ? '1' : null,
      data: atual.inicio.slice(0, 10), inicio: atual.inicio,
      slotProfissionalId: atual.profissionalId, criadoEm: atual.criadoEm };
  }

  nomeProfissional(): string {
    const atual = this.rascunho();
    return this.servico()?.profissionais.find(item => item.id === atual?.profissionalId)?.nome
      ?? 'Profissional indisponível';
  }

  revalidar(): void {
    const atual = this.rascunho();
    if (!atual) return;
    this.carregando.set(true);
    this.horarioValido.set(false);
    this.erro.set('');
    this.erroRede.set(false);
    this.erroServico.set(false);
    this.estabelecimentos.filialPublica(atual.unidadeId).subscribe({
      next: filial => {
        this.filial.set(filial);
        this.estabelecimentos.servicosDisponiveis(atual.unidadeId).subscribe({
          next: servicos => {
            const servico = servicos.find(item => item.id === atual.servicoId);
            if (!servico || !servico.profissionais.some(item => item.id === atual.profissionalId && item.ativo)) {
              this.erro.set('O serviço ou profissional escolhido não está mais disponível nesta filial.');
              this.erroServico.set(true);
              this.carregando.set(false);
              return;
            }
            this.servico.set(servico);
            this.estabelecimentos.horarios(atual.unidadeId, atual.servicoId,
              atual.inicio.slice(0, 10), atual.profissionalId).subscribe({
              next: consulta => {
                const livre = consulta.horarios.some(item =>
                  item.profissionalId === atual.profissionalId && item.inicio === atual.inicio);
                this.horarioValido.set(livre);
                if (!livre) this.erro.set('Este horário não está mais livre. Veja as alternativas da filial.');
                this.carregando.set(false);
              },
              error: () => {
                this.erro.set('Não foi possível conferir o horário. Suas escolhas foram preservadas.');
                this.erroRede.set(true);
                this.carregando.set(false);
              }
            });
          },
          error: () => {
            this.erro.set('Não foi possível conferir os serviços. Tente novamente.');
            this.erroRede.set(true);
            this.carregando.set(false);
          }
        });
      },
      error: erro => {
        const indisponivel = erro instanceof HttpErrorResponse && erro.status === 404;
        this.erro.set(indisponivel
          ? 'Esta filial foi desativada. Encontre outra para agendar.'
          : 'Não foi possível conferir a filial. Suas escolhas foram preservadas.');
        this.erroRede.set(!indisponivel);
        this.carregando.set(false);
      }
    });
  }

  confirmar(): void {
    const atual = this.rascunho();
    if (!atual || this.enviando() || !this.horarioValido() || this.auth.sessao()?.perfil !== 'CLIENTE') return;
    this.enviando.set(true);
    this.erro.set('');
    this.agendamentos.criar(atual.unidadeId, { servicoId: atual.servicoId,
      profissionalId: atual.profissionalId, inicio: atual.inicio }, this.chaveReserva).subscribe({
      next: reservado => {
        limparChaveIdempotencia(atual);
        this.sucesso.set(reservado);
        this.enviando.set(false);
      },
      error: erro => {
        this.enviando.set(false);
        if (erro instanceof HttpErrorResponse && erro.status === 409
            && erro.error?.codigo === 'HORARIO_INDISPONIVEL') {
          limparChaveIdempotencia(atual);
          void this.router.navigate(['/unidades', atual.unidadeId], {
            queryParams: { ...this.parametrosVoltar(3), inicio: null,
              slotProfissionalId: null, aviso: 'horario-indisponivel' }
          });
          return;
        }
        this.erro.set('Não foi possível confirmar o resultado. Tente novamente; a mesma solicitação será reutilizada.');
        this.erroRede.set(true);
      }
    });
  }
}
