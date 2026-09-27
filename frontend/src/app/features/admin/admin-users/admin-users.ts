import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { UserService } from '../../../core/services/user.service';
import { UserResponse } from '../../../shared/models/user.model';

const ALL_ROLES = ['ROLE_REQUESTER', 'ROLE_AGENT', 'ROLE_MANAGER', 'ROLE_ADMIN'];

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './admin-users.html',
  styleUrl: './admin-users.scss'
})
export class AdminUsers implements OnInit {
  users = signal<UserResponse[]>([]);
  loading = signal(true);
  errorMessage = signal<string | null>(null);

  showCreateForm = signal(false);
  creating = signal(false);
  availableRoles = ALL_ROLES;

  editingUserId = signal<number | null>(null);
  editingRoles = signal<Set<string>>(new Set());

  createForm: FormGroup;

  constructor(private fb: FormBuilder, private userService: UserService) {
    this.createForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      fullName: ['', Validators.required],
      password: ['', [Validators.required, Validators.minLength(8)]],
      roleNames: this.fb.array(ALL_ROLES.map(() => false))
    });
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.userService.listAll().subscribe({
      next: (users) => { this.users.set(users); this.loading.set(false); },
      error: () => { this.errorMessage.set('Could not load users.'); this.loading.set(false); }
    });
  }

  toggleCreateForm(): void {
    this.showCreateForm.update(v => !v);
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    const selectedRoles = ALL_ROLES.filter((_, i) => this.createForm.value.roleNames?.[i]);
    if (selectedRoles.length === 0) {
      this.errorMessage.set('Select at least one role.');
      return;
    }

    this.creating.set(true);
    this.errorMessage.set(null);

    const { email, fullName, password } = this.createForm.getRawValue();

    this.userService.create({ email: email!, fullName: fullName!, password: password!, roleNames: selectedRoles }).subscribe({
      next: () => {
        this.creating.set(false);
        this.showCreateForm.set(false);
        this.createForm.reset();
        this.loadUsers();
      },
      error: (err) => {
        this.creating.set(false);
        this.errorMessage.set(err.status === 409 ? 'That email is already in use.' : 'Could not create user.');
      }
    });
  }

  toggleActive(user: UserResponse): void {
    const action = user.active ? this.userService.deactivate(user.id) : this.userService.activate(user.id);
    action.subscribe({
      next: (updated) => {
        this.users.update(list => list.map(u => u.id === updated.id ? updated : u));
      },
      error: () => { this.errorMessage.set('Could not update user status.'); }
    });
  }

  startEditRoles(user: UserResponse): void {
    this.editingUserId.set(user.id);
    this.editingRoles.set(new Set(user.roles));
    this.errorMessage.set(null);
  }

  cancelEditRoles(): void {
    this.editingUserId.set(null);
  }

  toggleEditRole(role: string): void {
    this.editingRoles.update(set => {
      const next = new Set(set);
      if (next.has(role)) {
        next.delete(role);
      } else {
        next.add(role);
      }
      return next;
    });
  }

  saveRoles(userId: number): void {
    const roles = Array.from(this.editingRoles());
    if (roles.length === 0) {
      this.errorMessage.set('Select at least one role.');
      return;
    }

    this.userService.updateRoles(userId, roles).subscribe({
      next: (updated) => {
        this.users.update(list => list.map(u => u.id === updated.id ? updated : u));
        this.editingUserId.set(null);
      },
      error: () => { this.errorMessage.set('Could not update roles.'); }
    });
  }

  roleLabel(role: string): string {
    return role.replace('ROLE_', '');
  }

  initials(fullName: string): string {
    const parts = fullName.trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) { return '?'; }
    if (parts.length === 1) { return parts[0].slice(0, 2).toUpperCase(); }
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  avatarClass(fullName: string): string {
    let hash = 0;
    for (let i = 0; i < fullName.length; i++) {
      hash = (hash * 31 + fullName.charCodeAt(i)) >>> 0;
    }
    return `av-c${hash % 6}`;
  }
}