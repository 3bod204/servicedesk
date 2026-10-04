import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AssignTicketRequest, CreateTicketRequest, Page, TicketResponse, TicketSearchCriteria, UpdateStatusRequest } from '../../shared/models/ticket.model';
import { AuditEntryResponse } from '../../shared/models/audit.model';
import { CategoryResponse } from '../../shared/models/category.model';
import { QueueResponse } from '../../shared/models/queue.model';

@Injectable({ providedIn: 'root' })
export class TicketService {
  private readonly baseUrl = `${environment.apiBaseUrl}/tickets`;

  constructor(private http: HttpClient) {}

  search(criteria: TicketSearchCriteria): Observable<Page<TicketResponse>> {
    let params = new HttpParams();
    Object.entries(criteria).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return this.http.get<Page<TicketResponse>>(this.baseUrl, { params });
  }

  getById(id: number): Observable<TicketResponse> {
    return this.http.get<TicketResponse>(`${this.baseUrl}/${id}`);
  }

  create(request: CreateTicketRequest): Observable<TicketResponse> {
    return this.http.post<TicketResponse>(this.baseUrl, request);
  }

  getAudit(ticketId: number): Observable<AuditEntryResponse[]> {
  return this.http.get<AuditEntryResponse[]>(`${this.baseUrl}/${ticketId}/audit`);
  }

  changeStatus(ticketId: number, request: UpdateStatusRequest): Observable<TicketResponse> {
  return this.http.put<TicketResponse>(`${this.baseUrl}/${ticketId}/status`, request);
  }

  assign(ticketId: number, request: AssignTicketRequest): Observable<TicketResponse> {
  return this.http.put<TicketResponse>(`${this.baseUrl}/${ticketId}/assign`, request);
  }

  getCategories(): Observable<CategoryResponse[]> {
  return this.http.get<CategoryResponse[]>(`${this.baseUrl}/categories`);
  }

  getQueues(): Observable<QueueResponse[]> {
  return this.http.get<QueueResponse[]>(`${this.baseUrl}/queues`);
  }
}