import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./inbox/inbox.component').then((m) => m.InboxComponent) },
  { path: 'claims/new', loadComponent: () => import('./fnol/fnol.component').then((m) => m.FnolComponent) },
  { path: 'review-board', loadComponent: () => import('./review-board/review-board.component').then((m) => m.ReviewBoardComponent) },
  { path: 'claims/:id', loadComponent: () => import('./claim-detail/claim-detail.component').then((m) => m.ClaimDetailComponent) },
  { path: '**', redirectTo: '' },
];
