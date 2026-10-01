import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { AuthService } from './auth.service';

export const autenticadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const decidir = () => auth.sessao() ? true : router.createUrlTree(['/entrar']);
  return auth.sessao() === undefined ? auth.carregarSessao().pipe(map(decidir)) : of(decidir());
};

export const administradorGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const decidir = () => auth.sessao()?.perfil === 'ADMINISTRADOR'
    ? true
    : router.createUrlTree(['/']);
  return auth.sessao() === undefined ? auth.carregarSessao().pipe(map(decidir)) : of(decidir());
};

export const profissionalGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const decidir = () => auth.sessao()?.perfil === 'PROFISSIONAL'
    ? true : router.createUrlTree([auth.sessao() ? '/' : '/entrar']);
  return auth.sessao() === undefined ? auth.carregarSessao().pipe(map(decidir)) : of(decidir());
};

export const internoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const decidir = () => {
    const perfil = auth.sessao()?.perfil;
    return perfil && perfil !== 'CLIENTE' ? true
      : router.createUrlTree([perfil ? '/' : '/entrar']);
  };
  return auth.sessao() === undefined ? auth.carregarSessao().pipe(map(decidir)) : of(decidir());
};
