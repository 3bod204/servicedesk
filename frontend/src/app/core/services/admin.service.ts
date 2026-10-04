import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AdminQueue, AdminCategory, SlaPolicy,
  UpdateQueueRequest, UpdateCategoryRequest, UpdateSlaPolicyRequest
} from '../../shared/models/admin.model';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly baseUrl = `${environment.apiBaseUrl}/tickets/admin`;

  constructor(private http: HttpClient) {}

  listQueues(): Observable<AdminQueue[]> {
    return this.http.get<AdminQueue[]>(`${this.baseUrl}/queues`);
  }
  createQueue(request: UpdateQueueRequest): Observable<AdminQueue> {
    return this.http.post<AdminQueue>(`${this.baseUrl}/queues`, request);
  }
  updateQueue(id: number, request: UpdateQueueRequest): Observable<AdminQueue> {
    return this.http.put<AdminQueue>(`${this.baseUrl}/queues/${id}`, request);
  }

  listCategories(): Observable<AdminCategory[]> {
    return this.http.get<AdminCategory[]>(`${this.baseUrl}/categories`);
  }
  createCategory(request: UpdateCategoryRequest): Observable<AdminCategory> {
    return this.http.post<AdminCategory>(`${this.baseUrl}/categories`, request);
  }
  updateCategory(id: number, request: UpdateCategoryRequest): Observable<AdminCategory> {
    return this.http.put<AdminCategory>(`${this.baseUrl}/categories/${id}`, request);
  }

  listSlaPolicies(): Observable<SlaPolicy[]> {
    return this.http.get<SlaPolicy[]>(`${this.baseUrl}/sla-policies`);
  }
  updateSlaPolicy(id: number, request: UpdateSlaPolicyRequest): Observable<SlaPolicy> {
    return this.http.put<SlaPolicy>(`${this.baseUrl}/sla-policies/${id}`, request);
  }
}