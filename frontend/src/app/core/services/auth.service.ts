import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, RefreshRequest } from '../../shared/models/auth.model';
import { UserResponse } from '../../shared/models/user.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;
  private readonly usersUrl = `${environment.apiBaseUrl}/users`;

  accessToken = signal<string | null>(null);
  refreshToken = signal<string | null>(null);
  currentUser = signal<UserResponse | null>(null);

  constructor(private http: HttpClient) {}

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
  }

  private clearSession(): void {
    this.accessToken.set(null);
    this.refreshToken.set(null);
    this.currentUser.set(null);
  }
}