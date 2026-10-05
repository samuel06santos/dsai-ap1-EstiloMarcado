import { Component, OnDestroy, OnInit, signal } from '@angular/core';

interface DepoimentoDemonstrativo {
  nome: string;
  comentario: string;
  nota: number;
}

@Component({
  selector: 'app-testimonials-carousel',
  standalone: true,
  styleUrl: './testimonials-carousel.component.css',
  template: `
    <section class="testimonial-section" role="region" aria-roledescription="carrossel"
      aria-labelledby="testimonial-title" (pointerenter)="ponteiroDentro.set(true); sincronizarTimer()"
      (pointerleave)="ponteiroDentro.set(false); sincronizarTimer()"
      (focusin)="focoDentro.set(true); sincronizarTimer()" (focusout)="aoSairFoco($event)">
      <div class="testimonial-heading">
        <div>
          <p class="eyebrow">Uma prévia da experiência</p>
          <h2 id="testimonial-title">Agendar pode ser simples assim.</h2>
        </div>
      </div>

      <div class="testimonial-card">
        <div class="testimonial-rating" [attr.aria-label]="'Nota ilustrativa: ' + atual().nota + ' de 5 estrelas'">
          @for (estrela of estrelas; track estrela) {
            <span [class.unfilled]="estrela > atual().nota" aria-hidden="true">★</span>
          }
        </div>
        <blockquote>“{{ atual().comentario }}”</blockquote>
        <div class="testimonial-person">
          <span class="testimonial-avatar" aria-hidden="true">{{ atual().nome.charAt(0) }}</span>
          <div><strong>{{ atual().nome }}</strong></div>
        </div>
      </div>

      <div class="testimonial-controls">
        <div class="testimonial-position" aria-label="Posição no carrossel">
          <span>{{ indice() + 1 }} de {{ depoimentos.length }}</span>
          <div class="testimonial-dots">
            @for (depoimento of depoimentos; track depoimento.nome; let i = $index) {
              <button type="button" [class.active]="i === indice()"
                [attr.aria-label]="'Mostrar depoimento ' + (i + 1) + ' de ' + depoimentos.length"
                [attr.aria-current]="i === indice() ? 'true' : null" (click)="selecionar(i)"></button>
            }
          </div>
        </div>
        <div class="testimonial-buttons">
          <button type="button" (click)="selecionar(indice() - 1)"><</button>
          <button type="button" (click)="selecionar(indice() + 1)">></button>
          <button type="button" (click)="alternarReproducao()" [disabled]="movimentoReduzido()"
            [attr.title]="movimentoReduzido() ? 'Reprodução automática desativada pela preferência de movimento reduzido' : null">
            {{ pausaManual() || movimentoReduzido() ? '⏯' : '⏸' }}
          </button>
        </div>
      </div>
      <span class="testimonial-announcement" aria-live="polite" aria-atomic="true">{{ anuncio() }}</span>
    </section>
  `
})
export class TestimonialsCarouselComponent implements OnInit, OnDestroy {
  readonly depoimentos: readonly DepoimentoDemonstrativo[] = [
    { nome: 'Ana Claudia', comentario: 'Escolhi o serviço, encontrei um horário e entendi cada etapa antes de confirmar.', nota: 5 },
    { nome: 'Melissa Santos', comentario: 'Gostei de ver os horários disponíveis sem precisar começar por uma conversa.', nota: 5 },
    { nome: 'Pedro Sampaio', comentario: 'Ter meus agendamentos em um só lugar deixa tudo mais fácil de acompanhar.', nota: 4 },
    { nome: 'João Gomes', comentario: 'A revisão do pedido me ajudou a conferir o serviço e o horário com calma.', nota: 5 }
  ];
  readonly estrelas = [1, 2, 3, 4, 5];
  readonly indice = signal(0);
  readonly atual = () => this.depoimentos[this.indice()];
  readonly anuncio = signal('');
  readonly pausaManual = signal(false);
  readonly movimentoReduzido = signal(false);
  readonly ponteiroDentro = signal(false);
  readonly focoDentro = signal(false);

  private timer?: ReturnType<typeof setTimeout>;
  private media?: MediaQueryList;
  private readonly aoMudarPreferencia = (evento: MediaQueryListEvent): void => {
    this.movimentoReduzido.set(evento.matches);
    this.sincronizarTimer();
  };

  ngOnInit(): void {
    if (typeof window !== 'undefined' && 'matchMedia' in window) {
      this.media = window.matchMedia('(prefers-reduced-motion: reduce)');
      this.movimentoReduzido.set(this.media.matches);
      this.media.addEventListener('change', this.aoMudarPreferencia);
    }
    this.sincronizarTimer();
  }

  ngOnDestroy(): void {
    this.limparTimer();
    this.media?.removeEventListener('change', this.aoMudarPreferencia);
  }

  selecionar(indice: number): void {
    const novo = (indice + this.depoimentos.length) % this.depoimentos.length;
    this.indice.set(novo);
    this.anuncio.set(`Depoimento ${novo + 1} de ${this.depoimentos.length}: ${this.depoimentos[novo].nome}. Exemplo fictício.`);
    this.sincronizarTimer();
  }

  alternarReproducao(): void {
    this.pausaManual.update(pausado => !pausado);
    this.sincronizarTimer();
  }

  aoSairFoco(evento: FocusEvent): void {
    const regiao = evento.currentTarget as HTMLElement;
    if (evento.relatedTarget && regiao.contains(evento.relatedTarget as Node)) { return; }
    this.focoDentro.set(false);
    this.sincronizarTimer();
  }

  sincronizarTimer(): void {
    this.limparTimer();
    if (this.pausaManual() || this.movimentoReduzido() || this.ponteiroDentro() || this.focoDentro()) { return; }
    this.timer = setTimeout(() => {
      this.indice.update(indice => (indice + 1) % this.depoimentos.length);
      this.sincronizarTimer();
    }, 6000);
  }

  private limparTimer(): void {
    if (this.timer !== undefined) { clearTimeout(this.timer); this.timer = undefined; }
  }
}
