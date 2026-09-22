import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CommentResponse, CreateCommentRequest } from '../../shared/models/comment.model';

@Injectable({ providedIn: 'root' })
export class CommentService {
  constructor(private http: HttpClient) {}

  list(ticketId: number): Observable<CommentResponse[]> {
    return this.http.get<CommentResponse[]>(`${environment.apiBaseUrl}/tickets/${ticketId}/comments`);
  }

  create(ticketId: number, request: CreateCommentRequest): Observable<CommentResponse> {
    return this.http.post<CommentResponse>(`${environment.apiBaseUrl}/tickets/${ticketId}/comments`, request);
  }
}