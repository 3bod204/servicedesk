import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { UserService } from '../../../core/services/user.service';
import { AuthService } from '../../../core/services/auth.service';
import { UserResponse } from '../../../shared/models/user.model';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './profile.html',
  styleUrl: './profile.scss'
})
export class ProfileComponent implements OnInit {
  user = signal<UserResponse | null>(null);
  loading = signal(true);
  avatarFailed = signal(false);

  savingProfile = signal(false);
  profileSuccess = signal(false);
  profileError = signal<string | null>(null);

  savingPassword = signal(false);
  passwordSuccess = signal(false);
  passwordError = signal<string | null>(null);

  private fb = inject(FormBuilder);
  private userService = inject(UserService);
  private authService = inject(AuthService);

  profileForm = this.fb.group({
    fullName: ['', Validators.required],
    avatarUrl: ['']
  });

  passwordForm = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]]
  });

  ngOnInit(): void {
    this.userService.getMe().subscribe({
      next: (u) => {
        this.user.set(u);
        this.profileForm.patchValue({ fullName: u.fullName, avatarUrl: u.avatarUrl ?? '' });
        this.loading.set(false);
      },
      error: () => { this.loading.set(false); }
    });
  }

  get initials(): string {
    const name = this.user()?.fullName ?? '';
    return name.split(' ').map(p => p[0]).join('').slice(0, 2).toUpperCase();
  }

  roleLabel(role: string): string {
    return role.replace('ROLE_', '');
  }

  saveProfile(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }

    this.savingProfile.set(true);
    this.profileError.set(null);
    this.profileSuccess.set(false);

    const { fullName, avatarUrl } = this.profileForm.getRawValue();

    this.userService.updateProfile({ fullName: fullName!, avatarUrl: avatarUrl || null }).subscribe({
      next: (updated) => {
        this.user.set(updated);
        this.avatarFailed.set(false);
        this.authService.refreshCurrentUser();
        this.savingProfile.set(false);
        this.profileSuccess.set(true);
        setTimeout(() => this.profileSuccess.set(false), 3000);
      },
      error: () => {
        this.savingProfile.set(false);
        this.profileError.set('Could not update profile.');
      }
    });
  }

  savePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    this.savingPassword.set(true);
    this.passwordError.set(null);
    this.passwordSuccess.set(false);

    const { currentPassword, newPassword } = this.passwordForm.getRawValue();

    this.userService.changePassword({ currentPassword: currentPassword!, newPassword: newPassword! }).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.passwordSuccess.set(true);
        this.passwordForm.reset();
        setTimeout(() => this.passwordSuccess.set(false), 3000);
      },
      error: (err) => {
        this.savingPassword.set(false);
        this.passwordError.set(err.status === 401 ? 'Current password is incorrect.' : 'Could not change password.');
      }
    });
  }
}