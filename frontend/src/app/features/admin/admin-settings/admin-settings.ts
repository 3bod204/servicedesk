import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../../core/services/admin.service';
import { AdminQueue, AdminCategory, SlaPolicy } from '../../../shared/models/admin.model';

type Tab = 'queues' | 'categories' | 'sla';
const PRIORITY_ORDER = ['URGENT', 'HIGH', 'MEDIUM', 'LOW'];

@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-settings.html',
  styleUrl: './admin-settings.scss'
})
export class AdminSettings implements OnInit {
  activeTab = signal<Tab>('queues');
  errorMessage = signal<string | null>(null);

  queues = signal<AdminQueue[]>([]);
  categories = signal<AdminCategory[]>([]);
  slaPolicies = signal<SlaPolicy[]>([]);

  queueSearch = signal('');
  filteredQueues = computed(() => {
    const term = this.queueSearch().trim().toLowerCase();
    if (!term) return this.queues();
    return this.queues().filter(q =>
      q.name.toLowerCase().includes(term) || (q.description ?? '').toLowerCase().includes(term));
  });

  categorySearch = signal('');
  filteredCategories = computed(() => {
    const term = this.categorySearch().trim().toLowerCase();
    if (!term) return this.categories();
    return this.categories().filter(c =>
      c.name.toLowerCase().includes(term) || c.queueName.toLowerCase().includes(term));
  });

  showAddQueueModal = signal(false);
  newQueueName = '';
  newQueueDescription = '';
  editingQueueId = signal<number | null>(null);
  editQueueName = '';
  editQueueDescription = '';

  showAddCategoryModal = signal(false);
  newCategoryName = '';
  newCategoryQueueId: number | null = null;
  editingCategoryId = signal<number | null>(null);
  editCategoryName = '';
  editCategoryQueueId: number | null = null;

  
  editingSlaId = signal<number | null>(null);
  editFirstResponse = 1;
  editResolution = 1;

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadQueues();
    this.loadCategories();
    this.loadSla();
  }

  setTab(tab: Tab): void {
    this.activeTab.set(tab);
    this.errorMessage.set(null);
  }

  
  loadQueues(): void {
    this.adminService.listQueues().subscribe(q => this.queues.set(q));
  }

  openAddQueueModal(): void {
    this.newQueueName = '';
    this.newQueueDescription = '';
    this.errorMessage.set(null);
    this.showAddQueueModal.set(true);
  }

  closeAddQueueModal(): void {
    this.showAddQueueModal.set(false);
  }

  createQueue(): void {
    if (!this.newQueueName.trim()) return;
    this.errorMessage.set(null);

    this.adminService.createQueue({
      name: this.newQueueName.trim(),
      description: this.newQueueDescription.trim() || null,
      active: true
    }).subscribe({
      next: () => {
        this.newQueueName = '';
        this.newQueueDescription = '';
        this.showAddQueueModal.set(false);
        this.loadQueues();
      },
      error: (err) => this.errorMessage.set(
        err.status === 409 ? 'A queue with that name already exists.' : 'Could not create queue.')
    });
  }

  startEditQueue(q: AdminQueue): void {
    this.editingQueueId.set(q.id);
    this.editQueueName = q.name;
    this.editQueueDescription = q.description ?? '';
    this.errorMessage.set(null);
  }

  cancelEditQueue(): void {
    this.editingQueueId.set(null);
  }

  saveQueue(q: AdminQueue): void {
    if (!this.editQueueName.trim()) return;

    this.adminService.updateQueue(q.id, {
      name: this.editQueueName.trim(),
      description: this.editQueueDescription.trim() || null,
      active: q.active
    }).subscribe({
      next: (updated) => {
        this.queues.update(list => list.map(x => x.id === updated.id ? updated : x));
        this.editingQueueId.set(null);
      },
      error: () => this.errorMessage.set('Could not update queue.')
    });
  }

  toggleQueueActive(q: AdminQueue): void {
    this.adminService.updateQueue(q.id, {
      name: q.name, description: q.description, active: !q.active
    }).subscribe({
      next: (updated) => this.queues.update(list => list.map(x => x.id === updated.id ? updated : x)),
      error: () => this.errorMessage.set('Could not update queue status.')
    });
  }


  loadCategories(): void {
    this.adminService.listCategories().subscribe(c => this.categories.set(c));
  }

  openAddCategoryModal(): void {
    this.newCategoryName = '';
    this.newCategoryQueueId = null;
    this.errorMessage.set(null);
    this.showAddCategoryModal.set(true);
  }

  closeAddCategoryModal(): void {
    this.showAddCategoryModal.set(false);
  }

  createCategory(): void {
    if (!this.newCategoryName.trim() || this.newCategoryQueueId === null) return;
    this.errorMessage.set(null);

    this.adminService.createCategory({
      name: this.newCategoryName.trim(),
      queueId: this.newCategoryQueueId,
      active: true
    }).subscribe({
      next: () => {
        this.newCategoryName = '';
        this.newCategoryQueueId = null;
        this.showAddCategoryModal.set(false);
        this.loadCategories();
      },
      error: () => this.errorMessage.set('Could not create category (that name may already exist in this queue).')
    });
  }

  startEditCategory(c: AdminCategory): void {
    this.editingCategoryId.set(c.id);
    this.editCategoryName = c.name;
    this.editCategoryQueueId = c.queueId;
    this.errorMessage.set(null);
  }

  cancelEditCategory(): void {
    this.editingCategoryId.set(null);
  }

  saveCategory(c: AdminCategory): void {
    if (!this.editCategoryName.trim() || this.editCategoryQueueId === null) return;

    this.adminService.updateCategory(c.id, {
      name: this.editCategoryName.trim(),
      queueId: this.editCategoryQueueId,
      active: c.active
    }).subscribe({
      next: (updated) => {
        this.categories.update(list => list.map(x => x.id === updated.id ? updated : x));
        this.editingCategoryId.set(null);
      },
      error: () => this.errorMessage.set('Could not update category.')
    });
  }

  toggleCategoryActive(c: AdminCategory): void {
    this.adminService.updateCategory(c.id, {
      name: c.name, queueId: c.queueId, active: !c.active
    }).subscribe({
      next: (updated) => this.categories.update(list => list.map(x => x.id === updated.id ? updated : x)),
      error: () => this.errorMessage.set('Could not update category status.')
    });
  }

  
  loadSla(): void {
    this.adminService.listSlaPolicies().subscribe(policies => {
      this.slaPolicies.set(
        [...policies].sort((a, b) => PRIORITY_ORDER.indexOf(a.priority) - PRIORITY_ORDER.indexOf(b.priority)));
    });
  }

  startEditSla(p: SlaPolicy): void {
    this.editingSlaId.set(p.id);
    this.editFirstResponse = p.firstResponseMinutes;
    this.editResolution = p.resolutionMinutes;
    this.errorMessage.set(null);
  }

  cancelEditSla(): void {
    this.editingSlaId.set(null);
  }

  saveSla(p: SlaPolicy): void {
    if (this.editFirstResponse < 1 || this.editResolution < 1) {
      this.errorMessage.set('Both times must be at least 1 minute.');
      return;
    }
    if (this.editFirstResponse > this.editResolution) {
      this.errorMessage.set('First response time cannot be longer than resolution time.');
      return;
    }

    this.adminService.updateSlaPolicy(p.id, {
      firstResponseMinutes: this.editFirstResponse,
      resolutionMinutes: this.editResolution
    }).subscribe({
      next: (updated) => {
        this.slaPolicies.update(list => list.map(x => x.id === updated.id ? updated : x));
        this.editingSlaId.set(null);
      },
      error: () => this.errorMessage.set('Could not update SLA policy.')
    });
  }

  formatMinutes(total: number): string {
    const h = Math.floor(total / 60);
    const m = total % 60;
    if (h === 0) return `${m}m`;
    return m === 0 ? `${h}h` : `${h}h ${m}m`;
  }
}