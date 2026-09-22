import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, RefreshRequest } from '../../shared/models/auth.model';
import { UserResponse } from '../../shared/models/user.model';

const ACCESS_TOKEN_KEY = 'servicedesk.accessToken';
const REFRESH_TOKEN_KEY = 'servicedesk.refreshToken';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;
  private readonly usersUrl = `${environment.apiBaseUrl}/users`;
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  accessToken = signal<string | null>(this.readStorage(ACCESS_TOKEN_KEY));
  refreshToken = signal<string | null>(this.readStorage(REFRESH_TOKEN_KEY));
  currentUser = signal<UserResponse | null>(null);

  constructor(private http: HttpClient) {
    if (this.accessToken() !== null) {
      // Deferred: calling this synchronously would have the interceptor
      // re-inject AuthService while it's still under construction, causing
      // NG0200 (circular dependency) and wiping the session we just restored.
      setTimeout(() => {
        this.loadCurrentUser().subscribe({ error: () => this.clearSession() });
      });
    }
  }

  login(request: LoginRequest): Observable<UserResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, request).pipe(
      tap(response => this.setTokens(response)),
      switchMap(() => this.loadCurrentUser())
    );
  }

  refresh(): Observable<LoginResponse> {
    const request: RefreshRequest = { refreshToken: this.refreshToken() ?? '' };
    return this.http.post<LoginResponse>(`${this.baseUrl}/refresh`, request).pipe(
      tap(response => this.setTokens(response))
    );
  }

  logout(): void {
    const token = this.refreshToken();
    if (token) {
      this.http.post(`${this.baseUrl}/logout`, { refreshToken: token }).subscribe();
    }
    this.clearSession();
  }

  isLoggedIn(): boolean {
    return this.accessToken() !== null;
  }

  private loadCurrentUser(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.usersUrl}/me`).pipe(
      tap(user => this.currentUser.set(user))
    );
  }

  private setTokens(response: LoginResponse): void {
    this.accessToken.set(response.accessToken);
    this.refreshToken.set(response.refreshToken);
    this.writeStorage(ACCESS_TOKEN_KEY, response.accessToken);
    this.writeStorage(REFRESH_TOKEN_KEY, response.refreshToken);
  }

  private clearSession(): void {
    this.accessToken.set(null);
    this.refreshToken.set(null);
    this.currentUser.set(null);
    this.writeStorage(ACCESS_TOKEN_KEY, null);
    this.writeStorage(REFRESH_TOKEN_KEY, null);
  }

  private readStorage(key: string): string | null {
    if (!this.isBrowser) {
      return null;
    }
    return sessionStorage.getItem(key);
  }

  private writeStorage(key: string, value: string | null): void {
    if (!this.isBrowser) {
      return;
    }
    if (value === null) {
      sessionStorage.removeItem(key);
    } else {
      sessionStorage.setItem(key, value);
    }
  }
}