import { HttpClient } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { AuthService } from './auth.service';

interface PreReserva {
  filial: string;
  servico: string;
  preco: number;
  profissional: string;
  inicio: string;
  fusoHorario: string;
  expiraEm: string;
  estado: string;
}

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="auth-card wide">
      <p class="eyebrow">Checkout</p><h1>Revise sua pré-reserva</h1>
      @if (erro()) { <p class="notice error" role="alert">{{ erro() }}</p> }
      @if (reserva(); as r) {
        <p><strong>Filial:</strong> {{ r.filial }}</p>
        <p><strong>Serviço:</strong> {{ r.servico }}</p>
        <p><strong>Preço atual:</strong> R$ {{ r.preco }}</p>
        <p><strong>Profissional:</strong> {{ r.profissional }}</p>
        <p><strong>Horário local:</strong> {{ r.inicio }} ({{ r.fusoHorario }})</p>
        <p><strong>Pré-reserva válida até:</strong> {{ r.expiraEm }}</p>
        <p>Esta pré-reserva ainda não é um agendamento. O preço e as regras atuais serão conferidos na confirmação.</p>
        @if (r.estado === 'ATIVA') {
          @if (auth.sessao()?.perfil === 'CLIENTE') {
            <button class="button primary" type="button" [disabled]="enviando()" (click)="confirmar()">
              Confirmar agendamento</button>
          } @else if (!auth.sessao()) {
            <a class="button primary" [routerLink]="['/entrar']" [queryParams]="{retorno: retorno}">Entrar para confirmar</a>
          } @else { <p>Entre com uma conta de cliente para confirmar.</p> }
        } @else { <p>Esta pré-reserva não está mais disponível.</p><a routerLink="/filiais">Buscar outro horário</a> }
      }
      @if (agendamentoId()) { <p class="notice success">Agendamento criado: {{ agendamentoId() }}.</p> }
    </section>`
})
export class CheckoutComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  readonly reserva = signal<PreReserva | null>(null);
  readonly erro = signal('');
  readonly enviando = signal(false);
  readonly agendamentoId = signal<number | null>(null);
  readonly token = /^#t=([A-Za-z0-9_-]{43})$/.exec(window.location.hash)?.[1] ?? '';
  readonly retorno = this.token ? `/checkout#t=${this.token}` : '/checkout';

  ngOnInit(): void {
    if (!this.token) { this.erro.set('Link de pré-reserva inválido.'); return; }
    this.http.get('/api/autenticacao/csrf').pipe(switchMap(() =>
      this.http.post<PreReserva>('/api/checkout/pre-reservas/consulta', { token: this.token })))
      .subscribe({ next: r => this.reserva.set(r), error: () => this.erro.set('Pré-reserva não encontrada.') });
    this.auth.carregarSessao().subscribe();
  }

  confirmar(): void {
    if (!this.token || this.reserva()?.estado !== 'ATIVA' || this.enviando()) return;
    this.enviando.set(true);
    this.http.get('/api/autenticacao/csrf').pipe(switchMap(() =>
      this.http.post<{ id: number }>('/api/checkout/pre-reservas/confirmacoes', { token: this.token })))
      .subscribe({
        next: resposta => { this.agendamentoId.set(resposta.id); this.reserva.set(null); this.enviando.set(false); },
        error: () => { this.erro.set('Não foi possível confirmar. Atualize a página ou escolha outro horário.'); this.enviando.set(false); }
      });
  }
}
