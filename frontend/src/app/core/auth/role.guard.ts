import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export function roleGuard(allowedRoles: string[]): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    const userRoles = authService.currentUser()?.roles ?? [];
    const hasAccess = allowedRoles.some(role => userRoles.includes(role));

    if (hasAccess) {
      return true;
    }

    router.navigate(['/tickets']);
    return false;
  };
}