import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TicketService } from '../../../core/services/ticket.service';
import { CommentService } from '../../../core/services/comment.service';
import { AttachmentService } from '../../../core/services/attachment.service';
import { UserService } from '../../../core/services/user.service';
import { AuthService } from '../../../core/services/auth.service';
import { TicketResponse, TicketStatus } from '../../../shared/models/ticket.model';
import { CommentResponse } from '../../../shared/models/comment.model';
import { AttachmentResponse } from '../../../shared/models/attachment.model';
import { AuditEntryResponse } from '../../../shared/models/audit.model';
import { UserResponse } from '../../../shared/models/user.model';

const STATUS_LABELS: Record<TicketStatus, string> = {
  NEW: 'New', ASSIGNED: 'Assigned', IN_PROGRESS: 'In Progress',
  PENDING_REQUESTER: 'Pending', RESOLVED: 'Resolved', CLOSED: 'Closed', REOPENED: 'Reopened'
};

const STATUS_CLASS: Record<TicketStatus, string> = {
  NEW: 'new', ASSIGNED: 'assigned', IN_PROGRESS: 'in_progress',
  PENDING_REQUESTER: 'pending_requester', RESOLVED: 'resolved', CLOSED: 'closed', REOPENED: 'reopened'
};

// Mirrors backend TicketStatusTransitions exactly
const STATUS_TRANSITIONS: Record<TicketStatus, TicketStatus[]> = {
  NEW: ['ASSIGNED'],
  ASSIGNED: ['IN_PROGRESS'],
  IN_PROGRESS: ['PENDING_REQUESTER', 'RESOLVED'],
  PENDING_REQUESTER: ['IN_PROGRESS', 'RESOLVED'],
  RESOLVED: ['CLOSED', 'REOPENED'],
  CLOSED: ['REOPENED'],
  REOPENED: ['ASSIGNED', 'IN_PROGRESS']
};

const REOPEN_WINDOW_DAYS = 14;

@Component({
  selector: 'app-ticket-detail',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ticket-detail.html',
  styleUrl: './ticket-detail.scss'
})
export class TicketDetail implements OnInit {
  ticket = signal<TicketResponse | null>(null);
  comments = signal<CommentResponse[]>([]);
  attachments = signal<AttachmentResponse[]>([]);
  audit = signal<AuditEntryResponse[]>([]);
  queueMembers = signal<UserResponse[]>([]);
  loading = signal(true);
  errorMessage = signal<string | null>(null);

  commentBody = '';
  commentInternal = false;
  postingComment = signal(false);
  replyingTo = signal<CommentResponse | null>(null);

  uploadingFile = signal(false);

  draftStatus = signal<TicketStatus | null>(null);
  draftAssigneeId = signal<number | null>(null);
  savingChanges = signal(false);

  isDirty = computed(() => {
    const t = this.ticket();
    if (!t) return false;
    return this.draftStatus() !== t.status || this.draftAssigneeId() !== t.assigneeId;
  });

  // A brand-new ticket can only move forward by assigning it (the backend
  // auto-transitions NEW -> ASSIGNED when that happens), so lock the status
  // picker until an assignee is chosen.
  statusLocked = computed(() => {
    const t = this.ticket();
    if (!t) return true;
    return t.status === 'NEW' && this.draftAssigneeId() === null;
  });

  threadedComments = computed(() => {
    const all = this.comments();
    const topLevel = all.filter(c => !c.parentId);
    return topLevel.map(top => ({
      comment: top,
      replies: all.filter(r => r.parentId === top.id)
    }));
  });

  availableStatuses = computed<TicketStatus[]>(() => {
    const t = this.ticket();
    if (!t) return [];

    const next = [...STATUS_TRANSITIONS[t.status]];

    if (next.includes('REOPENED')) {
      const source = t.status === 'CLOSED' ? t.closedAt : t.resolvedAt;
      if (source) {
        const deadline = new Date(source).getTime() + REOPEN_WINDOW_DAYS * 24 * 60 * 60 * 1000;
        if (Date.now() > deadline) {
          return [t.status, ...next.filter(s => s !== 'REOPENED')];
        }
      }
    }

    return [t.status, ...next];
  });

  slaInfo = computed(() => {
    const t = this.ticket();
    if (!t) return { label: '', percent: 0, cssClass: 'sla-ok' };

    if (t.status === 'RESOLVED' || t.status === 'CLOSED') {
      return { label: 'Resolved ✓', percent: 100, cssClass: 'sla-ok' };
    }

    const created = new Date(t.createdAt).getTime();
    const due = new Date(t.slaDueAt).getTime();
    const now = Date.now();
    const budgetMs = due - created;
    const elapsedMs = now - created;
    const percentConsumed = Math.min(100, Math.max(0, (elapsedMs / budgetMs) * 100));
    const remainingMs = due - now;

    let cssClass = 'sla-ok';
    if (remainingMs < 0) cssClass = 'sla-breach';
    else if (percentConsumed >= 80) cssClass = 'sla-warn';

    const label = remainingMs < 0
      ? `Breached ${Math.abs(Math.round(remainingMs / 3600000))}h ago`
      : `${Math.round(remainingMs / 3600000)}h remaining`;

    return { label, percent: percentConsumed, cssClass };
  });

  constructor(
    private route: ActivatedRoute,
    private ticketService: TicketService,
    private commentService: CommentService,
    private attachmentService: AttachmentService,
    private userService: UserService,
    public authService: AuthService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loadAll(id);
  }

  loadAll(id: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.ticketService.getById(id).subscribe({
      next: (t) => {
        this.ticket.set(t);
        this.syncDrafts(t);
        this.loading.set(false);
        this.userService.getByQueue(t.queueId).subscribe(users => this.queueMembers.set(users));
      },
      error: () => { this.errorMessage.set('Could not load this ticket.'); this.loading.set(false); }
    });

    this.commentService.list(id).subscribe(c => this.comments.set(c));
    this.attachmentService.list(id).subscribe(a => this.attachments.set(a));
    this.ticketService.getAudit(id).subscribe(a => this.audit.set(a));
  }

  statusLabel(status: TicketStatus): string {
    return STATUS_LABELS[status];
  }

  statusClass(status: TicketStatus): string {
    return `b-${STATUS_CLASS[status]}`;
  }

  formatAuditValue(field: string, value: string | null): string {
    if (value === null) return field === 'assignee' ? 'Unassigned' : 'None';
    if (field === 'status') return STATUS_LABELS[value as TicketStatus] ?? value;
    return value;
  }

  priorityClass(priority: string): string {
    return priority.toLowerCase();
  }

  fileIconType(contentType: string): 'image' | 'pdf' | 'generic' {
    if (contentType.startsWith('image/')) return 'image';
    if (contentType === 'application/pdf') return 'pdf';
    return 'generic';
  }

  get isRequesterOnly(): boolean {
    const roles = this.authService.currentUser()?.roles ?? [];
    return roles.length > 0 && roles.every(r => r === 'ROLE_REQUESTER');
  }

  submitComment(): void {
    const t = this.ticket();
    if (!t || !this.commentBody.trim()) return;

    this.postingComment.set(true);
    const parentId = this.replyingTo()?.id;

    this.commentService.create(t.id, {
      body: this.commentBody,
      internal: this.commentInternal,
      ...(parentId ? { parentId } : {})
    }).subscribe({
      next: (comment) => {
        this.comments.update(list => [...list, comment]);
        this.commentBody = '';
        this.commentInternal = false;
        this.replyingTo.set(null);
        this.postingComment.set(false);
      },
      error: () => { this.postingComment.set(false); }
    });
  }

  startReply(comment: CommentResponse): void {
    this.replyingTo.set(comment);
  }

  cancelReply(): void {
    this.replyingTo.set(null);
  }

  private syncDrafts(t: TicketResponse): void {
    this.draftStatus.set(t.status);
    this.draftAssigneeId.set(t.assigneeId);
  }

  saveDetails(): void {
    const t = this.ticket();
    if (!t || !this.isDirty() || this.savingChanges()) return;

    this.errorMessage.set(null);
    this.savingChanges.set(true);

    const assigneeChanged = this.draftAssigneeId() !== t.assigneeId;

    if (assigneeChanged && this.draftAssigneeId() !== null) {
      this.ticketService.assign(t.id, { assigneeId: this.draftAssigneeId()! }).subscribe({
        next: (updated) => this.applyStatusIfNeeded(t, updated),
        error: () => {
          this.errorMessage.set("Could not assign — check the person belongs to this ticket's queue.");
          this.savingChanges.set(false);
        }
      });
    } else {
      this.applyStatusIfNeeded(t, t);
    }
  }

  private applyStatusIfNeeded(original: TicketResponse, current: TicketResponse): void {
    const wantedStatus = this.draftStatus();

    if (wantedStatus === null || wantedStatus === original.status || wantedStatus === current.status) {
      this.finishSave(current);
      return;
    }

    this.ticketService.changeStatus(current.id, { status: wantedStatus }).subscribe({
      next: (updated) => this.finishSave(updated),
      error: () => {
        this.errorMessage.set('That status change is not allowed from the current state.');
        this.ticket.set(current);
        this.savingChanges.set(false);
      }
    });
  }

  private finishSave(updated: TicketResponse): void {
    this.ticket.set(updated);
    this.syncDrafts(updated);
    this.savingChanges.set(false);
    this.ticketService.getAudit(updated.id).subscribe(a => this.audit.set(a));
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const t = this.ticket();
    if (!file || !t) return;

    this.uploadingFile.set(true);
    this.attachmentService.upload(t.id, file).subscribe({
      next: (attachment) => {
        this.attachments.update(list => [...list, attachment]);
        this.uploadingFile.set(false);
        input.value = '';
      },
      error: () => {
        this.errorMessage.set('Upload failed — check file type and size (max 10MB).');
        this.uploadingFile.set(false);
      }
    });
  }

  downloadAttachment(attachment: AttachmentResponse): void {
    this.attachmentService.download(attachment.id).subscribe(blob => {
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = attachment.filename;
      link.click();
      window.URL.revokeObjectURL(url);
    });
  }

  deleteAttachment(attachment: AttachmentResponse): void {
    if (!confirm(`Delete "${attachment.filename}"?`)) return;

    this.attachmentService.delete(attachment.id).subscribe({
      next: () => {
        this.attachments.update(list => list.filter(a => a.id !== attachment.id));
      },
      error: () => {
        this.errorMessage.set('Could not delete this attachment.');
      }
    });
  }

  canDeleteAttachment(attachment: AttachmentResponse): boolean {
    const user = this.authService.currentUser();
    if (!user) return false;
    return attachment.uploadedById === user.id || user.roles.includes('ROLE_ADMIN');
  }
}