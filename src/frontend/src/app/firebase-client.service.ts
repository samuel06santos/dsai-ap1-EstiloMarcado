import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { FirebaseApp, initializeApp } from 'firebase/app';
import { Auth, GoogleAuthProvider, applyActionCode, browserPopupRedirectResolver, initializeAuth,
  connectAuthEmulator, inMemoryPersistence, linkWithPopup, signInWithEmailAndPassword, signInWithPopup,
  signOut } from 'firebase/auth';

export interface FirebasePublicConfig {
  enabled: boolean;
  apiKey?: string;
  authDomain?: string;
  projectId?: string;
  appId?: string;
  emulatorUrl?: string;
}

@Injectable({ providedIn: 'root' })
export class FirebaseClientService {
  private readonly http = inject(HttpClient);
  private configPromise?: Promise<FirebasePublicConfig>;
  private app?: FirebaseApp;
  private auth?: Auth;
  private emulatorConnected = false;

  config(): Promise<FirebasePublicConfig> {
    this.configPromise ??= firstValueFrom(
      this.http.get<FirebasePublicConfig>('/api/autenticacao/firebase/config'))
      .catch(error => {
        this.configPromise = undefined;
        throw error;
      });
    return this.configPromise;
  }

  private async instance(): Promise<Auth> {
    const config = await this.config();
    if (!config.enabled || !config.apiKey || !config.projectId || !config.appId) {
      throw new Error('Firebase Authentication não está configurado.');
    }
    this.app ??= initializeApp({ apiKey: config.apiKey, authDomain: config.authDomain,
      projectId: config.projectId, appId: config.appId });
    this.auth ??= initializeAuth(this.app, {
      persistence: inMemoryPersistence, popupRedirectResolver: browserPopupRedirectResolver
    });
    if (config.emulatorUrl && !this.emulatorConnected) {
      connectAuthEmulator(this.auth, config.emulatorUrl, { disableWarnings: true });
      this.emulatorConnected = true;
    }
    return this.auth;
  }

  async passwordToken(email: string, password: string): Promise<string> {
    const auth = await this.instance();
    const result = await signInWithEmailAndPassword(auth, email, password);
    return result.user.getIdToken(true);
  }

  async googleToken(): Promise<string> {
    const auth = await this.instance();
    const result = await signInWithPopup(auth, new GoogleAuthProvider());
    return result.user.getIdToken(true);
  }

  async linkGoogle(email: string, password: string, expectedUid: string): Promise<string> {
    const auth = await this.instance();
    const result = await signInWithEmailAndPassword(auth, email, password);
    if (result.user.uid !== expectedUid) {
      await signOut(auth);
      throw new Error('Esta credencial pertence a outra conta.');
    }
    const linked = await linkWithPopup(result.user, new GoogleAuthProvider());
    return linked.user.getIdToken(true);
  }

  async applyVerificationCode(code: string): Promise<void> {
    await applyActionCode(await this.instance(), code);
  }

  async clear(): Promise<void> {
    if (this.auth?.currentUser) await signOut(this.auth);
  }

  static message(error: unknown): string {
    const code = (error as { code?: string })?.code;
    if (code === 'auth/popup-closed-by-user' || code === 'auth/cancelled-popup-request') {
      return 'A entrada com Google foi cancelada. Você pode tentar novamente ou usar e-mail e senha.';
    }
    if (code === 'auth/popup-blocked') {
      return 'O navegador bloqueou a janela do Google. Permita a janela ou use e-mail e senha.';
    }
    if (code === 'auth/account-exists-with-different-credential') {
      return 'Este e-mail já tem uma conta. Entre com sua senha e vincule o Google em Meu perfil.';
    }
    if (code === 'auth/credential-already-in-use') {
      return 'Esta conta Google já está vinculada a outro acesso. Solicite ajuda para resolver o vínculo.';
    }
    if (code === 'auth/invalid-action-code' || code === 'auth/expired-action-code') {
      return 'O link expirou ou já foi usado. Solicite um novo.';
    }
    return 'Não foi possível autenticar. Confira os dados e tente novamente.';
  }
}
