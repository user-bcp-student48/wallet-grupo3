import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type AccountType = 'PERSON' | 'BUSINESS';

export interface AccountRegistrationRequest {
  accountType: AccountType;
  documentNumber: string;
  firstNames?: string;
  paternalSurname?: string;
  maternalSurname?: string;
  legalName?: string;
  tradeName?: string;
  email?: string;
  phone?: string;
}

export interface GoogleRegistrationRequest {
  credential: string;
  documentNumber: string;
  phone?: string;
}

export interface RegisteredAccount {
  id: string;
  accountType: AccountType;
  documentType: 'DNI' | 'RUC';
  maskedDocument: string;
  status: string;
  createdAt: string;
}

export interface RegistrationConfig {
  googleEnabled: boolean;
  googleClientId: string;
}

@Injectable({ providedIn: 'root' })
export class OnboardingApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = 'http://localhost:8081/api/v1';

  health(): Observable<{ status: string; service: string }> {
    return this.http.get<{ status: string; service: string }>(`${this.apiBase}/health`);
  }

  registrationConfig(): Observable<RegistrationConfig> {
    return this.http.get<RegistrationConfig>(`${this.apiBase}/accounts/config`);
  }

  register(request: AccountRegistrationRequest): Observable<RegisteredAccount> {
    return this.http.post<RegisteredAccount>(`${this.apiBase}/accounts/register`, request);
  }

  registerWithGoogle(request: GoogleRegistrationRequest): Observable<RegisteredAccount> {
    return this.http.post<RegisteredAccount>(`${this.apiBase}/accounts/register/google`, request);
  }
}