import { Priority } from '../../shared/models/ticket.model';

export interface AdminQueue {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
}

export interface AdminCategory {
  id: number;
  name: string;
  queueId: number;
  queueName: string;
  active: boolean;
}

export interface SlaPolicy {
  id: number;
  priority: Priority;
  firstResponseMinutes: number;
  resolutionMinutes: number;
}

export interface UpdateQueueRequest {
  name: string;
  description: string | null;
  active: boolean;
}

export interface UpdateCategoryRequest {
  name: string;
  queueId: number;
  active: boolean;
}

export interface UpdateSlaPolicyRequest {
  firstResponseMinutes: number;
  resolutionMinutes: number;
}