import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

const COLLAPSED_KEY = 'servicedesk.sidebarCollapsed';

@Injectable({ providedIn: 'root' })
export class SidebarService {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  collapsed = signal<boolean>(this.readStorage());

  toggle(): void {
    this.collapsed.update(v => !v);
    this.writeStorage(this.collapsed());
  }

  private readStorage(): boolean {
    if (!this.isBrowser) {
      return true;
    }
    const stored = localStorage.getItem(COLLAPSED_KEY);
    return stored === null ? true : stored === 'true';
  }

  private writeStorage(value: boolean): void {
    if (!this.isBrowser) {
      return;
    }
    localStorage.setItem(COLLAPSED_KEY, String(value));
  }
}
