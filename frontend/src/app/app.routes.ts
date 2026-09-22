import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login';
import { AppShell } from './layout/app-shell/app-shell';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: AppShell,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'tickets', pathMatch: 'full' },
      { path: 'tickets', data: { breadcrumb: 'All Tickets' }, loadComponent: () => import('./features/tickets/ticket-list/ticket-list').then(m => m.TicketList) },
      { path: 'tickets/:id', data: { breadcrumb: 'Ticket Detail' }, loadComponent: () => import('./features/tickets/ticket-detail/ticket-detail').then(m => m.TicketDetail) },
    ]
  },
  { path: '**', redirectTo: 'login' },
];