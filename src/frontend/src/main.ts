import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter, Routes } from '@angular/router';
import { AppComponent } from './app/app.component';
import { administradorGuard, autenticadoGuard } from './app/auth.guard';
import { credentialsInterceptor } from './app/auth.service';
import { AtivacaoComponent, CadastroComponent, ContaComponent, HomeComponent, LoginComponent,
  NovaSenhaComponent, RecuperacaoComponent, UsuariosAdminComponent } from './app/auth-pages';

const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'entrar', component: LoginComponent },
  { path: 'cadastro', component: CadastroComponent },
  { path: 'ativar', component: AtivacaoComponent },
  { path: 'recuperar-conta', component: RecuperacaoComponent },
  { path: 'redefinir-senha', component: NovaSenhaComponent },
  { path: 'convite', component: NovaSenhaComponent, data: { convite: true } },
  { path: 'conta', component: ContaComponent, canActivate: [autenticadoGuard] },
  { path: 'administracao/usuarios', component: UsuariosAdminComponent, canActivate: [administradorGuard] },
  { path: '**', redirectTo: '' }
];

bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([credentialsInterceptor]),
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })
    )
  ]
}).catch((error: unknown) => console.error(error));
