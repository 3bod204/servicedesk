import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AssignTicketRequest, Page, TicketResponse, TicketSearchCriteria, UpdateStatusRequest } from '../../shared/models/ticket.model';
import { AuditEntryResponse } from '../../shared/models/audit.model';

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

  getAudit(ticketId: number): Observable<AuditEntryResponse[]> {
  return this.http.get<AuditEntryResponse[]>(`${this.baseUrl}/${ticketId}/audit`);
  }

  changeStatus(ticketId: number, request: UpdateStatusRequest): Observable<TicketResponse> {
  return this.http.put<TicketResponse>(`${this.baseUrl}/${ticketId}/status`, request);
  }

  assign(ticketId: number, request: AssignTicketRequest): Observable<TicketResponse> {
  return this.http.put<TicketResponse>(`${this.baseUrl}/${ticketId}/assign`, request);
  }
}