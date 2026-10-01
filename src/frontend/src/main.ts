import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter, Routes, withInMemoryScrolling } from '@angular/router';
import { AppComponent } from './app/app.component';
import { administradorGuard, autenticadoGuard, internoGuard, profissionalGuard } from './app/auth.guard';
import { credentialsInterceptor } from './app/auth.service';
import { AtivacaoComponent, CadastroComponent, ContaComponent, HomeComponent, LoginComponent,
  NovaSenhaComponent, RecuperacaoComponent, UsuariosAdminComponent } from './app/auth-pages';
import { EstabelecimentoAdminComponent, FilialPublicaComponent } from './app/estabelecimento-pages';
import { AgendaProfissionalComponent, MinhaFilialComponent } from './app/workspace-pages';
import { AdministracaoAgendaComponent, MinhaDisponibilidadeComponent } from './app/disponibilidade-pages';

const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'entrar', component: LoginComponent },
  { path: 'cadastro', component: CadastroComponent },
  { path: 'ativar', component: AtivacaoComponent },
  { path: 'recuperar-conta', component: RecuperacaoComponent },
  { path: 'redefinir-senha', component: NovaSenhaComponent },
  { path: 'convite', component: NovaSenhaComponent, data: { convite: true } },
  { path: 'conta', component: ContaComponent, canActivate: [autenticadoGuard] },
  { path: 'minha-filial', component: MinhaFilialComponent, canActivate: [internoGuard] },
  { path: 'profissional/agenda', component: AgendaProfissionalComponent,
    canActivate: [profissionalGuard] },
  { path: 'profissional/disponibilidade', component: MinhaDisponibilidadeComponent,
    canActivate: [profissionalGuard] },
  { path: 'administracao/usuarios', component: UsuariosAdminComponent, canActivate: [administradorGuard] },
  { path: 'administracao/agenda', component: AdministracaoAgendaComponent,
    canActivate: [administradorGuard] },
  { path: 'administracao/estabelecimento', component: EstabelecimentoAdminComponent,
    canActivate: [administradorGuard] },
  { path: 'unidades/:id', component: FilialPublicaComponent },
  { path: '**', redirectTo: '' }
];

bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' })),
    provideHttpClient(
      withInterceptors([credentialsInterceptor]),
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })
    )
  ]
}).catch((error: unknown) => console.error(error));
