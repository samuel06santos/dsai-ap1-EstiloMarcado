import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, map, merge, of, Subject, switchMap, tap } from 'rxjs';
import { FilialDescobertaService, FiltrosFiliais, PaginaFiliais, ServicoOpcao } from './filial-descoberta.service';
import { UiIconComponent } from './ui-icon.component';

@Component({
  standalone: true,
  imports: [CurrencyPipe, FormsModule, RouterLink, UiIconComponent],
  styleUrl: './filiais-publicas.component.css',
  template: `
    <div class="directory-page">
      <header class="directory-heading">
        <p class="eyebrow">Encontre seu próximo horário</p>
        <h1>Escolha uma filial para chamar de sua.</h1>
        <p>Explore serviços e encontre horários disponíveis sem precisar criar uma conta.</p>
      </header>

      <section class="directory-search" aria-label="Buscar filiais e serviços">
        <form (ngSubmit)="aplicarFiltros()">
          <div class="directory-search-field">
            <label for="busca-filial">Buscar filial ou estabelecimento</label>
            <input id="busca-filial" name="busca" type="search" maxlength="80" autocomplete="off"
              [(ngModel)]="buscaDigitada" (ngModelChange)="aoDigitar()"
              placeholder="Nome da filial ou do salão" aria-describedby="ajuda-busca">
            <small id="ajuda-busca">Digite ao menos 2 caracteres ou deixe vazio para ver todas.</small>
          </div>
          <div class="directory-service-filter" role="group" aria-label="Filtrar por serviço">
            <span class="directory-filter-label">Serviço</span>
            <div class="directory-tags">
              <button type="button" class="directory-tag" [class.is-selected]="servicosSelecionados.length === 0"
                [attr.aria-pressed]="servicosSelecionados.length === 0" (click)="selecionarTodos()">Todos</button>
              @for (opcao of opcoes(); track opcao.codigo) {
                <button type="button" class="directory-tag" [class.is-selected]="servicosSelecionados.includes(opcao.codigo)"
                  [attr.aria-pressed]="servicosSelecionados.includes(opcao.codigo)"
                  (click)="alternarServico(opcao.codigo)">{{ opcao.nome }}</button>
              }
            </div>
            <small>Selecione um ou mais serviços para encontrar filiais que ofereçam qualquer um deles.</small>
          </div>
          <button class="button primary" type="submit"><app-icon name="arrow" /> Buscar</button>
        </form>
        @if (buscaDigitada || servicosSelecionados.length) {
          <button type="button" class="directory-clear" (click)="limparFiltros()">Limpar filtros</button>
        }
        @if (avisoBusca()) { <p class="field-error" role="alert">{{ avisoBusca() }}</p> }
        @if (erroOpcoes()) { <p class="directory-option-error">Filtro de serviços indisponível no momento. A busca por nome continua funcionando.</p> }
      </section>

      <section class="directory-results" aria-label="Resultados da busca">
        @if (carregando()) {
          <p class="directory-status" role="status">Buscando filiais…</p>
          <div class="directory-grid" aria-hidden="true">
            <div class="directory-skeleton"></div><div class="directory-skeleton"></div>
            <div class="directory-skeleton"></div>
          </div>
        } @else if (erroConexao()) {
          <div class="directory-empty" role="alert">
            <app-icon name="building" />
            <h2>Não foi possível carregar as filiais.</h2>
            <p>Confira sua conexão e tente novamente.</p>
            <button type="button" class="button ghost" (click)="tentarNovamente()">Tentar novamente</button>
          </div>
        } @else if (resultado(); as pagina) {
          @if (pagina.total === 0) {
            <div class="directory-empty" role="status">
              <app-icon name="scissors" />
              <h2>Nenhuma filial encontrada.</h2>
              <p>Tente outro nome ou serviço para ver mais opções.</p>
              <button type="button" class="button ghost" (click)="limparFiltros()">Limpar filtros</button>
            </div>
          } @else if (pagina.itens.length === 0) {
            <div class="directory-empty" role="status">
              <h2>Esta página não tem resultados.</h2>
              <a class="button ghost" routerLink="/filiais" [queryParams]="paramsPagina(1)">Voltar à primeira página</a>
            </div>
          } @else {
            <div class="directory-results-heading">
              <h2>{{ pagina.total }} {{ pagina.total === 1 ? 'filial encontrada' : 'filiais encontradas' }}</h2>
              <span>Página {{ pagina.pagina }} de {{ pagina.totalPaginas }}</span>
            </div>
            <div class="directory-grid">
              @for (filial of pagina.itens; track filial.id) {
                <article class="directory-card">
                  <span class="directory-card-icon"><app-icon name="building" /></span>
                  <p class="directory-establishment">{{ filial.estabelecimento }}</p>
                  <h3>{{ filial.nome }}</h3>
                  @if (filial.endereco) { <p class="directory-address">{{ filial.endereco }}</p> }
                  <div class="directory-services" aria-label="Serviços disponíveis">
                    @for (servico of filial.servicos.slice(0, 3); track servico.codigo) {
                      <span>{{ servico.nome }}</span>
                    }
                    @if (filial.servicos.length > 3) { <span>+{{ filial.servicos.length - 3 }} serviços</span> }
                  </div>
                  @if (filial.precoMinimo !== null && filial.precoMaximo !== null) {
                    <p class="directory-price">
                      @if (filial.precoMinimo === filial.precoMaximo) {
                        {{ filial.precoMinimo | currency:'BRL' }}
                      } @else {
                        {{ filial.precoMinimo | currency:'BRL' }} – {{ filial.precoMaximo | currency:'BRL' }}
                      }
                    </p>
                  }
                  <a class="directory-card-link" [routerLink]="['/unidades', filial.id]"
                    [queryParams]="paramsPagina(pagina.pagina)">
                    Ver serviços e horários <app-icon name="arrow" />
                  </a>
                </article>
              }
            </div>
            @if (pagina.totalPaginas > 1) {
              <nav class="directory-pagination" aria-label="Páginas de filiais">
                @if (pagina.pagina > 1) {
                  <a class="button ghost" routerLink="/filiais" [queryParams]="paramsPagina(pagina.pagina - 1)"
                    aria-label="Página anterior de filiais">Anterior</a>
                }
                <span>Página {{ pagina.pagina }} de {{ pagina.totalPaginas }}</span>
                @if (pagina.pagina < pagina.totalPaginas) {
                  <a class="button ghost" routerLink="/filiais" [queryParams]="paramsPagina(pagina.pagina + 1)"
                    aria-label="Próxima página de filiais">Próxima</a>
                }
              </nav>
            }
          }
        }
      </section>
    </div>
  `
})
export class FiliaisPublicasComponent implements OnInit {
  private readonly api = inject(FilialDescobertaService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly buscaAlterada = new Subject<void>();
  private readonly recarregar = new Subject<void>();

  readonly opcoes = signal<ServicoOpcao[]>([]);
  readonly erroOpcoes = signal(false);
  readonly resultado = signal<PaginaFiliais | null>(null);
  readonly carregando = signal(true);
  readonly erroConexao = signal(false);
  readonly avisoBusca = signal('');
  readonly filtros = signal<FiltrosFiliais>({ busca: '', servicos: [], pagina: 1 });
  buscaDigitada = '';
  servicosSelecionados: string[] = [];

  ngOnInit(): void {
    this.buscaAlterada.pipe(debounceTime(400), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.aplicarFiltros());
    this.api.servicos().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: opcoes => this.opcoes.set(opcoes),
      error: () => this.erroOpcoes.set(true)
    });
    merge(this.route.queryParamMap,
      this.recarregar.pipe(map(() => this.route.snapshot.queryParamMap))).pipe(
      map(params => ({
        busca: params.get('busca') ?? '',
        servicos: [...new Set(params.getAll('servico').filter(Boolean))],
        pagina: Math.max(1, Number(params.get('pagina')) || 1)
      })),
      tap(filtros => {
        this.filtros.set(filtros);
        this.buscaDigitada = filtros.busca;
        this.servicosSelecionados = filtros.servicos;
        this.carregando.set(true);
        this.erroConexao.set(false);
      }),
      switchMap(filtros => this.api.listar(filtros).pipe(
        map(resultado => ({ resultado, erro: false })),
        catchError(() => of({ resultado: null, erro: true }))
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(({ resultado, erro }) => {
      this.resultado.set(resultado);
      this.erroConexao.set(erro);
      this.carregando.set(false);
    });
  }

  aoDigitar(): void { this.buscaAlterada.next(); }

  selecionarTodos(): void {
    this.servicosSelecionados = [];
    this.aplicarFiltros();
  }

  alternarServico(codigo: string): void {
    this.servicosSelecionados = this.servicosSelecionados.includes(codigo)
      ? this.servicosSelecionados.filter(servico => servico !== codigo)
      : [...this.servicosSelecionados, codigo];
    this.aplicarFiltros();
  }

  aplicarFiltros(): void {
    const busca = this.buscaDigitada.trim();
    if (busca.length === 1) {
      this.avisoBusca.set('Digite ao menos 2 caracteres ou limpe a busca.');
      return;
    }
    this.avisoBusca.set('');
    void this.router.navigate(['/filiais'], { queryParams: {
      busca: busca || null, servico: this.servicosSelecionados.length ? this.servicosSelecionados : null, pagina: 1
    } });
  }

  limparFiltros(): void {
    this.buscaDigitada = '';
    this.servicosSelecionados = [];
    this.avisoBusca.set('');
    this.aplicarFiltros();
  }

  tentarNovamente(): void {
    this.recarregar.next();
  }

  paramsPagina(pagina: number): Record<string, string | string[] | number | null> {
    return { busca: this.filtros().busca || null,
      servico: this.filtros().servicos.length ? this.filtros().servicos : null, pagina };
  }
}
