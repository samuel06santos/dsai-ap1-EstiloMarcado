import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class NotificacoesEstadoService {
  readonly revisao = signal(0);

  atualizarContagem(): void { this.revisao.update(atual => atual + 1); }
}
