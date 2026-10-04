export interface StatusCount {
  status: string;
  count: number;
}

export interface PriorityCount {
  priority: string;
  count: number;
}

export interface AverageTimeMetric {
  metricName: string;
  averageMinutes: number | null;
}

export interface SlaComplianceMetric {
  totalResolved: number;
  compliant: number;
  compliancePercentage: number | null;
}

export interface AgentWorkload {
  agentId: number;
  agentName: string;
  openTicketCount: number;
}

export interface ReportCriteria {
  dateFrom?: string;
  dateTo?: string;
  queueId?: number;
}