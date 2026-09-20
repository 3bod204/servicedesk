export interface AuditEntryResponse {
  id: number;
  field: string;
  oldValue: string | null;
  newValue: string | null;
  actorId: number;
  actorName: string;
  createdAt: string;
}