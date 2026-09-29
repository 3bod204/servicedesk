import { Component, OnInit, AfterViewInit, ViewChild, ElementRef, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { Chart, registerables } from 'chart.js';
import { ReportingService } from '../../core/services/reporting.service';
import { TicketService } from '../../core/services/ticket.service';
import {
  StatusCount, PriorityCount, AverageTimeMetric, SlaComplianceMetric, AgentWorkload, ReportCriteria
} from '../../shared/models/reporting.model';
import { QueueResponse } from '../../shared/models/queue.model';

Chart.register(...registerables);

const STATUS_COLORS: Record<string, string> = {
  NEW: '#2563EB', ASSIGNED: '#7C3AED', IN_PROGRESS: '#B45309',
  PENDING_REQUESTER: '#C2410C', RESOLVED: '#047857', CLOSED: '#374151', REOPENED: '#B91C1C'
};

const PRIORITY_COLORS: Record<string, string> = {
  LOW: '#15803D', MEDIUM: '#A16207', HIGH: '#EA580C', URGENT: '#DC2626'
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class DashboardComponent implements OnInit, AfterViewInit {
  @ViewChild('statusCanvas') statusCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('priorityCanvas') priorityCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('workloadCanvas') workloadCanvas!: ElementRef<HTMLCanvasElement>;

  queues = signal<QueueResponse[]>([]);
  selectedQueueId: number | null = null;
  dateFrom = '';
  dateTo = '';

  avgFirstResponse = signal<AverageTimeMetric | null>(null);
  avgResolution = signal<AverageTimeMetric | null>(null);
  slaCompliance = signal<SlaComplianceMetric | null>(null);

  loading = signal(true);
  exporting = signal(false);

  private statusChart?: Chart;
  private priorityChart?: Chart;
  private workloadChart?: Chart;
  private viewReady = false;
  private pendingData: {
    status: StatusCount[]; priority: PriorityCount[]; workload: AgentWorkload[];
  } | null = null;

  constructor(private reportingService: ReportingService, private ticketService: TicketService) {}

  ngOnInit(): void {
    this.ticketService.getQueues().subscribe(q => this.queues.set(q));
    this.loadAll();
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    if (this.pendingData) {
      this.renderCharts(this.pendingData.status, this.pendingData.priority, this.pendingData.workload);
    }
  }

  currentCriteria(): ReportCriteria {
    return {
      dateFrom: this.dateFrom ? new Date(this.dateFrom).toISOString() : undefined,
      dateTo: this.dateTo ? new Date(this.dateTo).toISOString() : undefined,
      queueId: this.selectedQueueId ?? undefined
    };
  }

  applyFilters(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.loading.set(true);
    const criteria = this.currentCriteria();

    forkJoin({
      status: this.reportingService.ticketsByStatus(criteria),
      priority: this.reportingService.ticketsByPriority(criteria),
      firstResponse: this.reportingService.avgFirstResponse(criteria),
      resolution: this.reportingService.avgResolution(criteria),
      compliance: this.reportingService.slaCompliance(criteria),
      workload: this.reportingService.agentWorkload(criteria)
    }).subscribe(result => {
      this.avgFirstResponse.set(result.firstResponse);
      this.avgResolution.set(result.resolution);
      this.slaCompliance.set(result.compliance);
      this.loading.set(false);

      if (this.viewReady) {
        this.renderCharts(result.status, result.priority, result.workload);
      } else {
        this.pendingData = { status: result.status, priority: result.priority, workload: result.workload };
      }
    });
  }

  private renderCharts(status: StatusCount[], priority: PriorityCount[], workload: AgentWorkload[]): void {
    this.statusChart?.destroy();
    this.statusChart = new Chart(this.statusCanvas.nativeElement, {
      type: 'doughnut',
      data: {
        labels: status.map(s => s.status),
        datasets: [{
          data: status.map(s => s.count),
          backgroundColor: status.map(s => STATUS_COLORS[s.status] ?? '#94A3B8'),
          borderWidth: 0
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'right', labels: { boxWidth: 12, font: { size: 11 } } } }
      }
    });

    this.priorityChart?.destroy();
    this.priorityChart = new Chart(this.priorityCanvas.nativeElement, {
      type: 'bar',
      data: {
        labels: priority.map(p => p.priority),
        datasets: [{
          data: priority.map(p => p.count),
          backgroundColor: priority.map(p => PRIORITY_COLORS[p.priority] ?? '#94A3B8'),
          borderRadius: 4
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
      }
    });

    this.workloadChart?.destroy();
    this.workloadChart = new Chart(this.workloadCanvas.nativeElement, {
      type: 'bar',
      data: {
        labels: workload.map(w => w.agentName),
        datasets: [{
          data: workload.map(w => w.openTicketCount),
          backgroundColor: '#2563EB',
          borderRadius: 4
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
      }
    });
  }

  exportFullReport(): void {
    this.exporting.set(true);
    const criteria = this.currentCriteria();

    forkJoin({
      status: this.reportingService.ticketsByStatus(criteria),
      priority: this.reportingService.ticketsByPriority(criteria),
      firstResponse: this.reportingService.avgFirstResponse(criteria),
      resolution: this.reportingService.avgResolution(criteria),
      compliance: this.reportingService.slaCompliance(criteria),
      workload: this.reportingService.agentWorkload(criteria)
    }).subscribe(result => {
      let csv = 'Tickets by Status\nstatus,count\n';
      result.status.forEach(r => csv += `${r.status},${r.count}\n`);
      csv += '\nTickets by Priority\npriority,count\n';
      result.priority.forEach(r => csv += `${r.priority},${r.count}\n`);
      csv += '\nAverage First Response Time\nmetric,minutes\n';
      csv += `${result.firstResponse.metricName},${result.firstResponse.averageMinutes ?? ''}\n`;
      csv += '\nAverage Resolution Time\nmetric,minutes\n';
      csv += `${result.resolution.metricName},${result.resolution.averageMinutes ?? ''}\n`;
      csv += '\nSLA Compliance\ntotal_resolved,compliant,percentage\n';
      csv += `${result.compliance.totalResolved},${result.compliance.compliant},${result.compliance.compliancePercentage ?? ''}\n`;
      csv += '\nAgent Workload\nagent_id,agent_name,open_ticket_count\n';
      result.workload.forEach(r => csv += `${r.agentId},${r.agentName},${r.openTicketCount}\n`);

      const blob = new Blob([csv], { type: 'text/csv' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'servicedesk-report.csv';
      link.click();
      window.URL.revokeObjectURL(url);
      this.exporting.set(false);
    });
  }
}