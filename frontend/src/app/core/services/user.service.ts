import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UserResponse } from '../../shared/models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private http: HttpClient) {}

  getByQueue(queueId: number): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(`${environment.apiBaseUrl}/users/by-queue/${queueId}`);
  }
}