import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError, BehaviorSubject, filter, take } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';

let isRefreshing = false;
const refreshedToken$ = new BehaviorSubject<string | null>(null);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const isAuthEndpoint = req.url.includes('/auth/login') || req.url.includes('/auth/refresh');

  const token = authService.accessToken();
  const authedReq = (token && !isAuthEndpoint)
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || isAuthEndpoint) {
        return throwError(() => error);
      }

      if (!isRefreshing) {
        isRefreshing = true;
        refreshedToken$.next(null);

        return authService.refresh().pipe(
          switchMap(response => {
            isRefreshing = false;
            refreshedToken$.next(response.accessToken);
            const retriedReq = req.clone({
              setHeaders: { Authorization: `Bearer ${response.accessToken}` }
            });
            return next(retriedReq);
          }),
          catchError(refreshError => {
            isRefreshing = false;
            router.navigate(['/login']);
            return throwError(() => refreshError);
          })
        );
      }

      return refreshedToken$.pipe(
        filter(token => token !== null),
        take(1),
        switchMap(token => {
          const retriedReq = req.clone({
            setHeaders: { Authorization: `Bearer ${token}` }
          });
          return next(retriedReq);
        })
      );
    })
  );
};