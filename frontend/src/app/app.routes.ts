import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login';
import { AppShell } from './layout/app-shell/app-shell';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: AppShell,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'tickets', pathMatch: 'full' },
      { path: 'tickets', data: { breadcrumb: 'All Tickets' }, loadComponent: () => import('./features/tickets/ticket-list/ticket-list').then(m => m.TicketListComponent) },
      { path: 'tickets/new', data: { breadcrumb: 'New Ticket' }, loadComponent: () => import('./features/tickets/ticket-create/ticket-create').then(m => m.TicketCreate) },
      { path: 'tickets/:id', data: { breadcrumb: 'Ticket Detail' }, loadComponent: () => import('./features/tickets/ticket-detail/ticket-detail').then(m => m.TicketDetail) },
      { path: 'dashboard', data: { breadcrumb: 'Dashboard' }, canActivate: [roleGuard(['ROLE_MANAGER', 'ROLE_ADMIN'])], loadComponent: () => import('./features/dashboard/dashboard').then(m => m.DashboardComponent) },
      { path: 'admin/users', data: { breadcrumb: 'Users' }, canActivate: [roleGuard(['ROLE_ADMIN'])], loadComponent: () => import('./features/admin/admin-users/admin-users').then(m => m.AdminUsers) },
      { path: 'admin/settings', data: { breadcrumb: 'Configuration' }, canActivate: [roleGuard(['ROLE_ADMIN'])], loadComponent: () => import('./features/admin/admin-settings/admin-settings').then(m => m.AdminSettings) },
      { path: 'profile', data: { breadcrumb: 'Profile' }, loadComponent: () => import('./features/profile/profile').then(m => m.ProfileComponent) },
    ]
  },
  { path: '**', redirectTo: 'login' },
];