import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TicketService } from '../../../core/services/ticket.service';
import { AuthService } from '../../../core/services/auth.service';
import { Priority, TicketResponse, TicketStatus } from '../../../shared/models/ticket.model';

type TabFilter = 'all' | 'open' | 'mine' | 'resolved';
type PriorityFilter = Priority | 'ALL';

const STATUS_LABELS: Record<TicketStatus, string> = {
  NEW: 'New', ASSIGNED: 'Assigned', IN_PROGRESS: 'In Progress',
  PENDING_REQUESTER: 'Pending', RESOLVED: 'Resolved', CLOSED: 'Closed', REOPENED: 'Reopened'
};

const STATUS_CLASS: Record<TicketStatus, string> = {
  NEW: 'new', ASSIGNED: 'assigned', IN_PROGRESS: 'in_progress',
  PENDING_REQUESTER: 'pending_requester', RESOLVED: 'resolved', CLOSED: 'closed', REOPENED: 'reopened'
};

@Component({
  selector: 'app-ticket-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ticket-list.html',
  styleUrl: './ticket-list.scss'
})
export class TicketList implements OnInit {
  tickets = signal<TicketResponse[]>([]);
  totalElements = signal(0);
  page = signal(0);
  pageSize = 20;
  activeTab = signal<TabFilter>('all');
  priorityFilter = signal<PriorityFilter>('ALL');
  loading = signal(true);
  errorMessage = signal<string | null>(null);

  filteredTickets = computed(() => {
    const tab = this.activeTab();
    const priority = this.priorityFilter();
    const myId = this.authService.currentUser()?.id;
    let all = this.tickets();

    if (tab === 'open') {
      all = all.filter(t => t.status !== 'RESOLVED' && t.status !== 'CLOSED');
    } else if (tab === 'mine') {
      all = all.filter(t => t.assigneeId === myId);
    } else if (tab === 'resolved') {
      all = all.filter(t => t.status === 'RESOLVED');
    }

    if (priority !== 'ALL') {
      all = all.filter(t => t.priority === priority);
    }

    return all;
  });

  constructor(
    private ticketService: TicketService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadTickets();
  }

  loadTickets(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.ticketService.search({ page: this.page(), size: this.pageSize }).subscribe({
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

  setTab(tab: TabFilter): void {
    this.activeTab.set(tab);
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
    const nowMs = Date.now();
    const diffHours = (dueMs - nowMs) / (1000 * 60 * 60);

    if (diffHours < 0) {
      return { label: `Breached ${Math.abs(Math.round(diffHours))}h ago`, cssClass: 'sla-breach' };
    }
    if (diffHours < 2) {
      return { label: `${Math.round(diffHours * 60)}m left`, cssClass: 'sla-warn' };
    }
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
}