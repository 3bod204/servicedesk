export type TicketStatus =
  | 'NEW'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'PENDING_REQUESTER'
  | 'RESOLVED'
  | 'CLOSED'
  | 'REOPENED';

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface TicketResponse {
  id: number;
  reference: string;
  title: string;
  description: string;
  status: TicketStatus;
  priority: Priority;
  categoryName: string;
  queueId: number;
  queueName: string;
  requesterId: number;
  requesterName: string;
  assigneeId: number | null;
  assigneeName: string | null;
  createdAt: string;
  slaDueAt: string;
  resolvedAt: string | null;
  closedAt: string | null;
}

export interface CreateTicketRequest {
  title: string;
  description: string;
  categoryId: number;
  priority: Priority;
}

export interface UpdateStatusRequest {
  status: TicketStatus;
}

export interface AssignTicketRequest {
  assigneeId: number;
}

export interface TicketSearchCriteria {
  status?: TicketStatus;
  priority?: Priority;
  queueId?: number;
  assigneeId?: number;
  categoryId?: number;
  createdFrom?: string;
  createdTo?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}