import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterLink, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header><a class="brand" routerLink="/">Estilo <em>Marcado</em></a><nav>
      @if (auth.sessao(); as sessao) {
        <a routerLink="/conta">Minha conta</a>
        @if (sessao.perfil === 'ADMINISTRADOR') { <a routerLink="/administracao/usuarios">Equipe</a> }
        <button class="link-button" (click)="sair()">Sair</button>
      } @else { <a routerLink="/entrar">Entrar</a><a class="nav-cta" routerLink="/cadastro">Criar conta</a> }
    </nav></header>
    <main><router-outlet /></main>
    <footer>Estilo Marcado · agendamentos com tranquilidade</footer>
  `
})
export class AppComponent implements OnInit {
  readonly auth = inject(AuthService); private readonly router = inject(Router);
  ngOnInit(): void { this.auth.carregarSessao().subscribe(); }
  sair(): void { this.auth.logout().subscribe(() => void this.router.navigateByUrl('/')); }
}
