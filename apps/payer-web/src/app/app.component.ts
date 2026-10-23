import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectButtonModule } from 'primeng/selectbutton';
import { AccountRegistrationRequest, AccountType, OnboardingApiService, RegisteredAccount } from './onboarding-api.service';

declare global {
  interface Window {
    google?: {
      accounts?: {
        id?: {
          initialize: (options: {
            client_id: string;
            callback: (response: { credential: string }) => void;
          }) => void;
          renderButton: (parent: HTMLElement, options: Record<string, string | number | boolean>) => void;
        };
      };
    };
  }
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ButtonModule, FormsModule, InputTextModule, MessageModule, SelectButtonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, AfterViewInit {
  private readonly api = inject(OnboardingApiService);

  private googleButtonElement?: ElementRef<HTMLDivElement>;

  @ViewChild('googleButton')
  set googleButton(element: ElementRef<HTMLDivElement> | undefined) {
    this.googleButtonElement = element;
    this.renderGoogleButton();
  }

  readonly accountTypes = [
    { label: 'Persona', value: 'PERSON' as const },
    { label: 'Negocio', value: 'BUSINESS' as const }
  ];
  readonly accountType = signal<AccountType>('PERSON');
  readonly documentNumber = signal('');
  readonly firstNames = signal('');
  readonly paternalSurname = signal('');
  readonly maternalSurname = signal('');
  readonly legalName = signal('');
  readonly tradeName = signal('');
  readonly email = signal('');
  readonly phone = signal('');
  readonly googleEnabled = signal(false);
  readonly googleClientId = signal('');
  readonly googleCredential = signal('');
  readonly registeredAccount = signal<RegisteredAccount | null>(null);
  readonly serviceStatus = signal<'checking' | 'online' | 'offline'>('checking');
  readonly busy = signal(false);
  readonly googleLoading = signal(false);
  readonly errorMessage = signal('');
  readonly noticeMessage = signal('');
  private viewReady = false;
  private googleRendered = false;
  private googleSdkPromise: Promise<void> | null = null;

  ngOnInit(): void {
    this.api.registrationConfig().subscribe({
      next: (config) => {
        this.googleEnabled.set(config.googleEnabled);
        this.googleClientId.set(config.googleClientId);
        this.serviceStatus.set('online');
        this.renderGoogleButton();
      },
      error: () => this.serviceStatus.set('offline')
    });
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.renderGoogleButton();
  }

  changeAccountType(accountType: AccountType): void {
    this.accountType.set(accountType);
    this.googleCredential.set('');
    this.errorMessage.set('');
    this.noticeMessage.set('');
  }

  submitRegistration(): void {
    this.errorMessage.set('');
    const documentNumber = this.documentNumber().replace(/\s/g, '');
    let request: AccountRegistrationRequest;

    if (this.accountType() === 'PERSON') {
      if (!/^\d{8}$/.test(documentNumber)) {
        this.errorMessage.set('Ingresa un DNI válido de 8 dígitos.');
        return;
      }
      request = {
        accountType: 'PERSON',
        documentNumber,
        firstNames: this.firstNames().trim(),
        paternalSurname: this.paternalSurname().trim(),
        maternalSurname: this.maternalSurname().trim(),
        email: this.email().trim(),
        phone: this.phone().trim()
      };
    } else {
      if (!/^\d{11}$/.test(documentNumber)) {
        this.errorMessage.set('Ingresa un RUC válido de 11 dígitos.');
        return;
      }
      request = {
        accountType: 'BUSINESS',
        documentNumber,
        legalName: this.legalName().trim(),
        tradeName: this.tradeName().trim(),
        email: this.email().trim(),
        phone: this.phone().trim()
      };
    }

    this.busy.set(true);
    this.api.register(request)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (account) => this.registeredAccount.set(account),
        error: (error: HttpErrorResponse) => this.errorMessage.set(this.describeError(error))
      });
  }

  submitGoogleRegistration(): void {
    const documentNumber = this.documentNumber().replace(/\s/g, '');
    if (!/^\d{8}$/.test(documentNumber)) {
      this.errorMessage.set('Completa tu DNI de 8 dígitos para finalizar el registro.');
      return;
    }
    const credential = this.googleCredential();
    if (!credential) return;

    this.errorMessage.set('');
    this.busy.set(true);
    this.api.registerWithGoogle({ credential, documentNumber, phone: this.phone().trim() })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (account) => this.registeredAccount.set(account),
        error: (error: HttpErrorResponse) => this.errorMessage.set(this.describeError(error))
      });
  }

  startOver(): void {
    this.registeredAccount.set(null);
    this.accountType.set('PERSON');
    this.documentNumber.set('');
    this.firstNames.set('');
    this.paternalSurname.set('');
    this.maternalSurname.set('');
    this.legalName.set('');
    this.tradeName.set('');
    this.email.set('');
    this.phone.set('');
    this.googleCredential.set('');
    this.errorMessage.set('');
    this.noticeMessage.set('');
    this.googleRendered = false;
    this.renderGoogleButton();
  }

  private renderGoogleButton(): void {
    const clientId = this.googleClientId();
    if (!this.viewReady || !this.googleEnabled() || !clientId || !this.googleButtonElement || this.googleRendered) return;

    this.googleRendered = true;
    this.googleLoading.set(true);
    this.loadGoogleSdk().then(() => {
      const googleIdentity = window.google?.accounts?.id;
      const host = this.googleButtonElement?.nativeElement;
      if (!googleIdentity || !host) return;

      googleIdentity.initialize({
        client_id: clientId,
        callback: (response) => {
          this.googleCredential.set(response.credential);
          this.noticeMessage.set('Google confirmó tu identidad. Completa el DNI para finalizar.');
          this.errorMessage.set('');
        }
      });
      googleIdentity.renderButton(host, {
        theme: 'outline', size: 'large', shape: 'rectangular',
        text: 'continue_with', logo_alignment: 'left', width: 300
      });
    }).catch(() => {
      this.errorMessage.set('No se pudo cargar el acceso de Google. Puedes continuar con tus datos.');
      this.googleRendered = false;
    }).finally(() => this.googleLoading.set(false));
  }

  private loadGoogleSdk(): Promise<void> {
    if (window.google?.accounts?.id) return Promise.resolve();
    if (this.googleSdkPromise) return this.googleSdkPromise;

    this.googleSdkPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Google Identity Services no está disponible'));
      document.head.appendChild(script);
    });
    return this.googleSdkPromise;
  }

  private describeError(error: HttpErrorResponse): string {
    const code = typeof error.error?.error === 'string' ? error.error.error : '';
    if (error.status === 0) return 'No se pudo conectar con el servicio local. Comprueba que esté iniciado.';
    if (error.status === 409) return 'Ya existe una cuenta registrada con ese documento.';
    if (error.status === 503) return 'El registro con Google aún no está configurado en este entorno.';
    if (error.status === 502) return 'Google no pudo verificar la identidad en este momento. Inténtalo nuevamente.';
    if (error.status === 400 && code) return code;
    return code ? `La solicitud no se completó: ${code}.` : `La solicitud no se completó (HTTP ${error.status}).`;
  }
}