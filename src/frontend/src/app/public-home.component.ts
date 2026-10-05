import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from './auth.service';
import { PainelClienteComponent } from './painel-cliente-pages';
import { TestimonialsCarouselComponent } from './testimonials-carousel.component';
import { UiIconComponent } from './ui-icon.component';

@Component({
  standalone: true,
  imports: [RouterLink, UiIconComponent, PainelClienteComponent, TestimonialsCarouselComponent],
  styleUrl: './public-home.component.css',
  template: `
    @if (auth.sessao(); as sessao) {
      @if (sessao.perfil === 'CLIENTE') {
        <app-painel-cliente />
      } @else {
        <section class="dashboard-welcome">
          <div class="welcome-copy">
            <p class="eyebrow">Seu espaço</p>
            <h1>Olá, {{ sessao.nome }}.</h1>
            <p class="lead">Tudo o que você precisa para cuidar do seu tempo, em um só lugar.</p>
            <div class="actions">
              @switch (sessao.perfil) {
                @case ('PROFISSIONAL') { <a class="button primary" routerLink="/profissional/agenda"><app-icon name="calendar" /> Ver minha agenda</a> }
                @case ('ADMINISTRADOR') { <a class="button primary" routerLink="/administracao/estabelecimento"><app-icon name="building" /> Ver meu painel</a> }
                @case ('RECEPCAO') { <a class="button primary" routerLink="/equipe/agendamentos"><app-icon name="calendar" /> Ver agenda da filial</a> }
              }
            </div>
          </div>
          <div class="welcome-art" aria-hidden="true"><span>EM</span><i></i><i></i></div>
        </section>
      }
    } @else {
      <div class="public-home">
        <section class="home-hero" aria-labelledby="home-title">
          <div class="home-hero-copy">
            <p class="eyebrow">Seu tempo, bem cuidado</p>
            <h1 id="home-title">Encontre seu horário de beleza sem trocar mensagens.</h1>
            <p class="lead">Escolha o serviço, veja horários disponíveis e agende em poucos passos.</p>
            <div class="actions">
              <a class="button primary" routerLink="/filiais"><app-icon name="calendar" /> Encontrar horário</a>
              <a class="button ghost" routerLink="/" fragment="como-funciona">Como funciona <app-icon name="arrow" /></a>
            </div>
            <p class="home-hero-note"><app-icon name="check" /> Explore serviços e horários sem criar conta.</p>
          </div>
          <div class="home-hero-art" aria-hidden="true">
            <div class="home-art-circle home-art-circle-one"></div>
            <div class="home-art-circle home-art-circle-two"></div>
            <div class="home-art-card">
              <span class="home-art-monogram">EM<span class="home-art-sparkle">✦</span></span>
              <span class="home-art-label">Um cuidado no seu tempo</span>
              <span class="home-art-line"><app-icon name="scissors" /> Escolha o serviço</span>
              <span class="home-art-line"><app-icon name="calendar" /> Encontre o horário</span>
              <span class="home-art-line"><app-icon name="check" /> Confirme quando quiser</span>
            </div>
            <span class="home-art-chip"><app-icon name="sparkles" /> Seu momento começa aqui</span>
          </div>
        </section>

        <section class="home-section home-steps" id="como-funciona" aria-labelledby="steps-title">
          <div class="home-section-heading">
            <p class="eyebrow">Sem complicação</p>
            <h2 id="steps-title">Do primeiro clique ao horário reservado.</h2>
            <p>Três passos claros para cuidar do que importa.</p>
          </div>
          <div class="home-step-grid">
            <article class="home-step-card">
              <span class="home-step-icon"><app-icon name="building" /></span><span class="home-step-number">01</span>
              <h3>Escolha onde ir</h3><p>Encontre a filial e o serviço que combinam com você.</p>
            </article>
            <article class="home-step-card">
              <span class="home-step-icon"><app-icon name="clock" /></span><span class="home-step-number">02</span>
              <h3>Veja os horários</h3><p>Confira a disponibilidade e selecione o melhor momento.</p>
            </article>
            <article class="home-step-card">
              <span class="home-step-icon"><app-icon name="check" /></span><span class="home-step-number">03</span>
              <h3>Revise e confirme</h3><p>Confira os detalhes antes de concluir o agendamento.</p>
            </article>
          </div>
        </section>

        <section class="home-section home-benefits" aria-labelledby="benefits-title">
          <div class="home-section-heading">
            <p class="eyebrow">Tudo em seu lugar</p>
            <h2 id="benefits-title">Mais clareza para quem agenda e para quem atende.</h2>
          </div>
          <div class="home-benefit-grid">
            <article class="home-benefit-card">
              <span class="home-benefit-tag">Para clientes</span>
              <h3>Seu próximo cuidado, no seu ritmo.</h3>
              <p>Veja horários atualizados, revise a reserva e acompanhe seus agendamentos na conta.</p>
              <a routerLink="/filiais">Explorar filiais <app-icon name="arrow" /></a>
            </article>
            <article class="home-benefit-card home-benefit-card-team">
              <span class="home-benefit-tag">Para a equipe</span>
              <h3>Uma agenda mais fácil de acompanhar.</h3>
              <p>Profissionais e recepção consultam a agenda; a administração organiza serviços e disponibilidade.</p>
              <a routerLink="/entrar">Acessar minha conta <app-icon name="arrow" /></a>
            </article>
          </div>
        </section>

        <app-testimonials-carousel />

        <section class="home-final-cta" aria-labelledby="final-cta-title">
          <div><p class="eyebrow">Seu próximo momento</p><h2 id="final-cta-title">Pronto para encontrar um horário?</h2></div>
          <a class="button primary" routerLink="/filiais">Encontrar horário <app-icon name="arrow" /></a>
        </section>
      </div>
    }
  `
})
export class HomeComponent { readonly auth = inject(AuthService); }
