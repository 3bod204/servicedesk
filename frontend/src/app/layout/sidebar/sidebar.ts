import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { SidebarService } from '../../core/services/sidebar.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss'
})
export class SidebarComponent {
  constructor(public authService: AuthService, public sidebarService: SidebarService) {}

  get isAdmin(): boolean {
    const roles = this.authService.currentUser()?.roles ?? [];
    return roles.includes('ROLE_ADMIN');
  }

  get isManagerOrAdmin(): boolean {
    const roles = this.authService.currentUser()?.roles ?? [];
    return roles.some(r => r === 'ROLE_MANAGER' || r === 'ROLE_ADMIN');
  }

  get initials(): string {
    const name = this.authService.currentUser()?.fullName ?? '';
    return name.split(' ').map(p => p[0]).join('').slice(0, 2).toUpperCase();
  }

  get roleLabel(): string {
    const roles = this.authService.currentUser()?.roles ?? [];
    if (roles.includes('ROLE_ADMIN')) return 'Admin';
    if (roles.includes('ROLE_MANAGER')) return 'Manager';
    if (roles.includes('ROLE_AGENT')) return 'Agent';
    return 'Requester';
  }
}