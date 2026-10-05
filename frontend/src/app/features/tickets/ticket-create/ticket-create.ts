import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { TicketService } from '../../../core/services/ticket.service';
import { AttachmentService } from '../attachment.service';
import { CategoryResponse } from '../../../shared/models/category.model';
import { Priority } from '../../../shared/models/ticket.model';

const MAX_FILES = 5;
const MAX_SIZE_BYTES = 10 * 1024 * 1024;
const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

@Component({
  selector: 'app-ticket-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './ticket-create.html',
  styleUrl: './ticket-create.scss'
})
export class TicketCreate implements OnInit {
  private fb = inject(FormBuilder);
  private ticketService = inject(TicketService);
  private attachmentService = inject(AttachmentService);
  private router = inject(Router);

  priorities = PRIORITIES;

  form = this.fb.group({
    title: ['', Validators.required],
    description: ['', Validators.required],
    categoryId: [null as number | null, Validators.required],
    priority: ['MEDIUM' as Priority, Validators.required]
  });

  categories = signal<CategoryResponse[]>([]);
  selectedFiles = signal<File[]>([]);
  isDragOver = signal(false);
  submitting = signal(false);
  errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.ticketService.getCategories().subscribe(cats => this.categories.set(cats));
  }

  selectPriority(priority: Priority): void {
    this.form.patchValue({ priority });
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver.set(true);
  }

  onDragLeave(): void {
    this.isDragOver.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver.set(false);
    const files = Array.from(event.dataTransfer?.files ?? []);
    this.addFiles(files);
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    this.addFiles(files);
    input.value = '';
  }

  private addFiles(files: File[]): void {
    if (this.selectedFiles().length + files.length > MAX_FILES) {
      this.errorMessage.set(`You can attach at most ${MAX_FILES} files.`);
      return;
    }
    const oversized = files.find(f => f.size > MAX_SIZE_BYTES);
    if (oversized) {
      this.errorMessage.set(`"${oversized.name}" exceeds the 10MB limit.`);
      return;
    }
    this.errorMessage.set(null);
    this.selectedFiles.update(list => [...list, ...files]);
  }

  removeFile(file: File): void {
    this.selectedFiles.update(list => list.filter(f => f !== file));
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { title, description, categoryId, priority } = this.form.getRawValue();

    this.ticketService.create({
      title: title!, description: description!, categoryId: categoryId!, priority: priority!
    }).subscribe({
      next: (ticket) => {
        const files = this.selectedFiles();
        if (files.length === 0) {
          this.router.navigate(['/tickets', ticket.id]);
          return;
        }
        forkJoin(files.map(f => this.attachmentService.upload(ticket.id, f))).subscribe({
          next: () => this.router.navigate(['/tickets', ticket.id]),
          error: () => this.router.navigate(['/tickets', ticket.id])
        });
      },
      error: () => {
        this.errorMessage.set('Could not create the ticket. Please check the form and try again.');
        this.submitting.set(false);
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/tickets']);
  }
}