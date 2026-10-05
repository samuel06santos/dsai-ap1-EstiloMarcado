import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter, Routes, withInMemoryScrolling } from '@angular/router';
import { AppComponent } from './app/app.component';
import { administradorGuard, autenticadoGuard, equipeGuard, internoGuard, profissionalGuard } from './app/auth.guard';
import { credentialsInterceptor } from './app/auth.service';
import { AtivacaoComponent, CadastroComponent, ContaComponent, FirebaseEmailActionComponent, LoginComponent,
  NovaSenhaComponent, RecuperacaoComponent, UsuariosAdminComponent } from './app/auth-pages';
import { HomeComponent } from './app/public-home.component';
import { EstabelecimentoAdminComponent, FilialPublicaComponent } from './app/estabelecimento-pages';
import { AgendaProfissionalComponent, MinhaFilialComponent } from './app/workspace-pages';
import { AdministracaoAgendaComponent, MinhaDisponibilidadeComponent } from './app/disponibilidade-pages';
import { MeusAgendamentosComponent, RevisaoAgendamentoComponent } from './app/agendamento-pages';
import { AgendaOperacionalComponent } from './app/agenda-operacional.component';
import { FilaEquipeComponent, HistoricoAgendamentoComponent, ListaEsperaComponent, NotificacoesComponent,
  RelatorioOperacionalComponent } from './app/operacao-pages';

const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'entrar', component: LoginComponent },
  { path: 'cadastro', component: CadastroComponent },
  { path: 'ativar', component: AtivacaoComponent },
  { path: 'acao-email', component: FirebaseEmailActionComponent },
  { path: 'recuperar-conta', component: RecuperacaoComponent },
  { path: 'redefinir-senha', component: NovaSenhaComponent },
  { path: 'convite', component: NovaSenhaComponent, data: { convite: true } },
  { path: 'conta', component: ContaComponent, canActivate: [autenticadoGuard] },
  { path: 'meus-agendamentos', component: MeusAgendamentosComponent, canActivate: [autenticadoGuard] },
  { path: 'lista-espera', component: ListaEsperaComponent, canActivate: [autenticadoGuard] },
  { path: 'notificacoes', component: NotificacoesComponent, canActivate: [autenticadoGuard] },
  { path: 'agendamentos/:id/historico', component: HistoricoAgendamentoComponent,
    canActivate: [autenticadoGuard] },
  { path: 'equipe/lista-espera', component: FilaEquipeComponent, canActivate: [equipeGuard] },
  { path: 'administracao/relatorios', component: RelatorioOperacionalComponent,
    canActivate: [administradorGuard] },
  { path: 'minha-filial', component: MinhaFilialComponent, canActivate: [internoGuard] },
  { path: 'equipe/agendamentos', component: AgendaOperacionalComponent, canActivate: [equipeGuard] },
  { path: 'profissional/agenda', component: AgendaProfissionalComponent,
    canActivate: [profissionalGuard] },
  { path: 'profissional/disponibilidade', component: MinhaDisponibilidadeComponent,
    canActivate: [profissionalGuard] },
  { path: 'administracao/usuarios', component: UsuariosAdminComponent, canActivate: [administradorGuard] },
  { path: 'administracao/agenda', component: AdministracaoAgendaComponent,
    canActivate: [administradorGuard] },
  { path: 'administracao/estabelecimento', component: EstabelecimentoAdminComponent,
    canActivate: [administradorGuard] },
  { path: 'unidades/:id/revisar', component: RevisaoAgendamentoComponent },
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
