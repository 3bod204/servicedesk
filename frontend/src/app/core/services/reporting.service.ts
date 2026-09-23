import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  StatusCount, PriorityCount, AverageTimeMetric, SlaComplianceMetric, AgentWorkload, ReportCriteria
} from '../../shared/models/reporting.model';

@Injectable({ providedIn: 'root' })
export class ReportingService {
  private readonly baseUrl = `${environment.apiBaseUrl}/reports`;

  constructor(private http: HttpClient) {}

  private buildParams(criteria: ReportCriteria): HttpParams {
    let params = new HttpParams();
    Object.entries(criteria).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return params;
  }

  ticketsByStatus(criteria: ReportCriteria): Observable<StatusCount[]> {
    return this.http.get<StatusCount[]>(`${this.baseUrl}/tickets-by-status`, { params: this.buildParams(criteria) });
  }

  ticketsByPriority(criteria: ReportCriteria): Observable<PriorityCount[]> {
    return this.http.get<PriorityCount[]>(`${this.baseUrl}/tickets-by-priority`, { params: this.buildParams(criteria) });
  }

  avgFirstResponse(criteria: ReportCriteria): Observable<AverageTimeMetric> {
    return this.http.get<AverageTimeMetric>(`${this.baseUrl}/avg-first-response-time`, { params: this.buildParams(criteria) });
  }

  avgResolution(criteria: ReportCriteria): Observable<AverageTimeMetric> {
    return this.http.get<AverageTimeMetric>(`${this.baseUrl}/avg-resolution-time`, { params: this.buildParams(criteria) });
  }

  slaCompliance(criteria: ReportCriteria): Observable<SlaComplianceMetric> {
    return this.http.get<SlaComplianceMetric>(`${this.baseUrl}/sla-compliance`, { params: this.buildParams(criteria) });
  }

  agentWorkload(criteria: ReportCriteria): Observable<AgentWorkload[]> {
    return this.http.get<AgentWorkload[]>(`${this.baseUrl}/agent-workload`, { params: this.buildParams(criteria) });
  }
}