import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChangePasswordRequest, CreateUserRequest, UpdateProfileRequest, UserResponse } from '../../shared/models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private http: HttpClient) {}

  getByQueue(queueId: number): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(`${environment.apiBaseUrl}/users/by-queue/${queueId}`);
  }

  getAssignable(): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(`${environment.apiBaseUrl}/users/assignable`);
  }

  listAll(): Observable<UserResponse[]> {
  return this.http.get<UserResponse[]>(environment.apiBaseUrl + '/users');
}

  create(request: CreateUserRequest): Observable<UserResponse> {
  return this.http.post<UserResponse>(environment.apiBaseUrl + '/users', request);
}

  deactivate(userId: number): Observable<UserResponse> {
  return this.http.put<UserResponse>(`${environment.apiBaseUrl}/users/${userId}/deactivate`, {});
}

  activate(userId: number): Observable<UserResponse> {
  return this.http.put<UserResponse>(`${environment.apiBaseUrl}/users/${userId}/activate`, {});
}

updateRoles(userId: number, roleNames: string[]): Observable<UserResponse> {
  return this.http.put<UserResponse>(`${environment.apiBaseUrl}/users/${userId}/roles`, { roleNames });
}

getMe(): Observable<UserResponse> {
  return this.http.get<UserResponse>(`${environment.apiBaseUrl}/users/me`);
}

updateProfile(request: UpdateProfileRequest): Observable<UserResponse> {
  return this.http.put<UserResponse>(`${environment.apiBaseUrl}/users/me`, request);
}

changePassword(request: ChangePasswordRequest): Observable<void> {
  return this.http.put<void>(`${environment.apiBaseUrl}/users/me/password`, request);
}
}