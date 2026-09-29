import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./inbox/inbox.component').then((m) => m.InboxComponent) },
  { path: 'claims/new', loadComponent: () => import('./fnol/fnol.component').then((m) => m.FnolComponent) },
  { path: 'claims/:id', loadComponent: () => import('./claim-detail/claim-detail.component').then((m) => m.ClaimDetailComponent) },
  { path: '**', redirectTo: '' },
];
