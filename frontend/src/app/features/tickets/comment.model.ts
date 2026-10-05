export interface CommentResponse {
  id: number;
  ticketId: number;
  parentId: number | null;
  authorId: number;
  authorName: string;
  body: string;
  internal: boolean;
  createdAt: string;
}

export interface CreateCommentRequest {
  body: string;
  internal: boolean;
  parentId?: number;
}