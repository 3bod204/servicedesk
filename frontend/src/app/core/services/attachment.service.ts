import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AttachmentResponse } from '../../shared/models/attachment.model';

@Injectable({ providedIn: 'root' })
export class AttachmentService {
  constructor(private http: HttpClient) {}

  list(ticketId: number): Observable<AttachmentResponse[]> {
    return this.http.get<AttachmentResponse[]>(`${environment.apiBaseUrl}/tickets/${ticketId}/attachments`);
  }

  upload(ticketId: number, file: File): Observable<AttachmentResponse> {
  const formData = new FormData();
  formData.append('file', file);
  return this.http.post<AttachmentResponse>(
    `${environment.apiBaseUrl}/tickets/${ticketId}/attachments`, formData
  );
}

  download(attachmentId: number): Observable<Blob> {
  return this.http.get(`${environment.apiBaseUrl}/attachments/${attachmentId}/download`, {
    responseType: 'blob'
  });
  }

  delete(attachmentId: number): Observable<void> {
  return this.http.delete<void>(`${environment.apiBaseUrl}/attachments/${attachmentId}`);
  }
}