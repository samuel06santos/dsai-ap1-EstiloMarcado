import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { EstadoLista } from './estados-interface';
import { IconName, UiIconComponent } from './ui-icon.component';

/**
 * Estado padronizado de uma listagem. A tela decide o texto conforme o contexto
 * e projeta as ações (limpar filtros, tentar novamente, cadastrar, etc.).
 * Use apenas quando a classificação não for `conteudo`; a lista em si é da tela.
 */
@Component({
  selector: 'app-estado-lista',
  standalone: true,
  imports: [UiIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (estado()) {
      @case ('carregando') {
        <p class="loading-state" role="status">{{ titulo() || 'Carregando…' }}</p>
      }
      @case ('erro') {
        <div class="empty-state is-erro" role="alert">
          <span class="panel-icon"><app-icon [name]="icone()" /></span>
          <h3>{{ titulo() || 'Não foi possível carregar' }}</h3>
          @if (descricao()) { <p>{{ descricao() }}</p> }
          <div class="form-actions"><ng-content /></div>
        </div>
      }
      @case ('sem-resultado') {
        <div class="empty-state" role="status">
          <span class="panel-icon"><app-icon [name]="icone()" /></span>
          <h3>{{ titulo() || 'Nenhum resultado com os filtros atuais' }}</h3>
          @if (descricao()) { <p>{{ descricao() }}</p> }
          <div class="form-actions"><ng-content /></div>
        </div>
      }
      @case ('sem-dados') {
        <div class="empty-state" role="status">
          <span class="panel-icon"><app-icon [name]="icone()" /></span>
          <h3>{{ titulo() }}</h3>
          @if (descricao()) { <p>{{ descricao() }}</p> }
          <div class="form-actions"><ng-content /></div>
        </div>
      }
    }
  `
})
export class EstadoListaComponent {
  readonly estado = input.required<EstadoLista>();
  readonly titulo = input('');
  readonly descricao = input('');
  readonly icone = input<IconName>('calendar');
}
