export interface AttachmentResponse {
  id: number;
  ticketId: number;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedById: number;
  uploadedByName: string;
  uploadedAt: string;
}