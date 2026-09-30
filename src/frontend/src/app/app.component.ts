import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main>
      <p class="eyebrow">Ambiente de desenvolvimento</p>
      <h1>Estilo Marcado</h1>
      <p>A plataforma de agendamento esta sendo preparada.</p>
    </main>
  `,
  styles: `
    main {
      max-width: 720px;
      margin: 12vh auto;
      padding: 2rem;
      background: #ffffff;
      border-radius: 1rem;
      box-shadow: 0 10px 28px rgb(30 30 47 / 10%);
    }

    .eyebrow {
      color: #7c3aed;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
  `
})
export class AppComponent {}
