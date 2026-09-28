import { Component, OnInit, effect, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TicketService } from '../../../core/services/ticket.service';
import { AuthService } from '../../../core/services/auth.service';
import { UserService } from '../../../core/services/user.service';
import { TicketResponse, TicketStatus, Priority, TicketSearchCriteria } from '../../../shared/models/ticket.model';
import { QueueResponse } from '../../../shared/models/queue.model';
import { UserResponse } from '../../../shared/models/user.model';

const STATUS_LABELS: Record<TicketStatus, string> = {
  NEW: 'New', ASSIGNED: 'Assigned', IN_PROGRESS: 'In Progress',
  PENDING_REQUESTER: 'Pending', RESOLVED: 'Resolved', CLOSED: 'Closed', REOPENED: 'Reopened'
};

const STATUS_CLASS: Record<TicketStatus, string> = {
  NEW: 'new', ASSIGNED: 'assigned', IN_PROGRESS: 'in_progress',
  PENDING_REQUESTER: 'pending_requester', RESOLVED: 'resolved', CLOSED: 'closed', REOPENED: 'reopened'
};

const ALL_STATUSES: TicketStatus[] = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'PENDING_REQUESTER', 'RESOLVED', 'CLOSED', 'REOPENED'];

@Component({
  selector: 'app-ticket-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ticket-list.html',
  styleUrl: './ticket-list.scss'
})
export class TicketListComponent implements OnInit {
  tickets = signal<TicketResponse[]>([]);
  totalElements = signal(0);
  page = signal(0);
  pageSize = 20;
  loading = signal(true);
  errorMessage = signal<string | null>(null);
  exporting = signal(false);
  filtersOpen = signal(false);

  allStatuses = ALL_STATUSES;
  queues = signal<QueueResponse[]>([]);
  assignableUsers = signal<UserResponse[]>([]);

  // Real filter state, sent directly to the backend
  filterStatus: TicketStatus | '' = '';
  filterPriority: Priority | '' = '';
  filterDateFrom = '';
  filterDateTo = '';
  filterSearch = '';
  filterMineOnly = false;
  filterQueueId: number | '' = '';
  filterAssigneeId: number | '' = '';

  private assignableUsersLoaded = false;

  constructor(
    private ticketService: TicketService,
    private authService: AuthService,
    private userService: UserService,
    private router: Router
  ) {
    // currentUser() loads asynchronously after login/refresh, so fetch once it's
    // actually available rather than checking it once during ngOnInit.
    effect(() => {
      if (!this.assignableUsersLoaded && this.canFilterByAssignee) {
        this.assignableUsersLoaded = true;
        this.userService.getAssignable().subscribe({
          next: (users) => this.assignableUsers.set(users),
          error: () => {}
        });
      }
    });
  }

  ngOnInit(): void {
    this.loadTickets();
    this.ticketService.getQueues().subscribe({ next: (queues) => this.queues.set(queues) });
  }

  private buildCriteria(): TicketSearchCriteria {
    const myId = this.authService.currentUser()?.id;
    return {
      status: this.filterStatus || undefined,
      priority: this.filterPriority || undefined,
      queueId: this.filterQueueId || undefined,
      assigneeId: this.filterMineOnly ? myId : (this.filterAssigneeId || undefined),
      createdFrom: this.filterDateFrom ? new Date(this.filterDateFrom).toISOString() : undefined,
      createdTo: this.filterDateTo ? new Date(this.filterDateTo).toISOString() : undefined,
      search: this.filterSearch || undefined
    };
  }

  loadTickets(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.ticketService.search({ ...this.buildCriteria(), page: this.page(), size: this.pageSize }).subscribe({
      next: (result) => {
        this.tickets.set(result.content);
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load tickets. Please try again.');
        this.loading.set(false);
      }
    });
  }

  applyFilters(): void {
    this.page.set(0);
    this.loadTickets();
  }

  clearFilters(): void {
    this.filterStatus = '';
    this.filterPriority = '';
    this.filterDateFrom = '';
    this.filterDateTo = '';
    this.filterSearch = '';
    this.filterMineOnly = false;
    this.filterQueueId = '';
    this.filterAssigneeId = '';
    this.applyFilters();
  }

  toggleFilters(): void {
    this.filtersOpen.update(open => !open);
  }

  get activeFilterCount(): number {
    return [
      this.filterStatus, this.filterPriority, this.filterDateFrom, this.filterDateTo,
      this.filterMineOnly, this.filterQueueId, this.filterAssigneeId
    ].filter(v => v !== '' && v !== false).length;
  }

  get canExport(): boolean {
    const roles = this.authService.currentUser()?.roles ?? [];
    return roles.some(r => r === 'ROLE_MANAGER' || r === 'ROLE_ADMIN' || r === 'ROLE_AGENT');
  }

  get canFilterByAssignee(): boolean {
    return this.canExport;
  }

  statusLabel(status: TicketStatus): string {
    return STATUS_LABELS[status];
  }

  statusClass(status: TicketStatus): string {
    return `b-${STATUS_CLASS[status]}`;
  }

  priorityClass(priority: string): string {
    return priority.toLowerCase();
  }

  slaInfo(ticket: TicketResponse): { label: string; cssClass: string } {
    if (ticket.status === 'RESOLVED' || ticket.status === 'CLOSED') {
      return { label: '✓ Done', cssClass: 'sla-ok' };
    }
    const dueMs = new Date(ticket.slaDueAt).getTime();
    const diffHours = (dueMs - Date.now()) / (1000 * 60 * 60);

    if (diffHours < 0) return { label: `Breached ${Math.abs(Math.round(diffHours))}h ago`, cssClass: 'sla-breach' };
    if (diffHours < 2) return { label: `${Math.round(diffHours * 60)}m left`, cssClass: 'sla-warn' };
    return { label: `${Math.round(diffHours)}h left`, cssClass: 'sla-ok' };
  }

  openTicket(id: number): void {
    this.router.navigate(['/tickets', id]);
  }

  createTicket(): void {
    this.router.navigate(['/tickets/new']);
  }

  nextPage(): void {
    if ((this.page() + 1) * this.pageSize < this.totalElements()) {
      this.page.update(p => p + 1);
      this.loadTickets();
    }
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update(p => p - 1);
      this.loadTickets();
    }
  }

  exportCsv(): void {
    this.exporting.set(true);
    this.ticketService.search({ ...this.buildCriteria(), page: 0, size: 100000 }).subscribe({
      next: (result) => {
        const headers = ['reference', 'title', 'status', 'priority', 'category', 'queue', 'requester', 'assignee', 'created', 'sla_due'];
        let csv = headers.join(',') + '\n';

        result.content.forEach(t => {
          const row = [
            t.reference,
            `"${t.title.replace(/"/g, '""')}"`,
            t.status,
            t.priority,
            t.categoryName,
            t.queueName,
            t.requesterName,
            t.assigneeName ?? 'Unassigned',
            t.createdAt,
            t.slaDueAt
          ];
          csv += row.join(',') + '\n';
        });

        const blob = new Blob([csv], { type: 'text/csv' });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'tickets-export.csv';
        link.click();
        window.URL.revokeObjectURL(url);
        this.exporting.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not export tickets.');
        this.exporting.set(false);
      }
    });
  }
}