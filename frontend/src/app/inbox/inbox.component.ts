import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';

import { ClaimsService } from '../claims.service';
import { Claim, ClaimStatus } from '../models';

const STATUSES: (ClaimStatus | 'ALL')[] = [
  'ALL', 'NOTIFIED', 'UNDER_REVIEW', 'RESERVED', 'SETTLED', 'CLOSED', 'REJECTED',
];

@Component({
  selector: 'app-inbox',
  imports: [RouterLink, DatePipe],
  template: `
    <div class="head">
      <h1>Claims inbox</h1>
      <div class="filters">
        @for (s of statuses; track s) {
          <button class="chip" [class.active]="filter() === s" (click)="setFilter(s)">{{ s }}</button>
        }
      </div>
    </div>

    @if (error()) { <p class="error">{{ error() }}</p> }
    @if (loading()) { <p class="muted">Loading…</p> }

    @if (!loading() && claims().length === 0) {
      <p class="muted">No claims @if (filter() !== 'ALL') { with status {{ filter() }} }.</p>
    }

    @if (claims().length > 0) {
      <table>
        <thead>
          <tr><th>Claim</th><th>Policy</th><th>Peril</th><th>Claimant</th><th>Status</th><th>Loss date</th><th>Registered</th></tr>
        </thead>
        <tbody>
          @for (c of claims(); track c.claimNumber) {
            <tr>
              <td><a [routerLink]="['/claims', c.claimNumber]" class="mono link">{{ c.claimNumber }}</a></td>
              <td class="mono">{{ c.policyNumber }}</td>
              <td>{{ c.peril }}</td>
              <td>{{ c.claimantName }}</td>
              <td><span class="badge" [attr.data-s]="c.status">{{ c.status }}</span></td>
              <td>{{ c.lossDate }}</td>
              <td>{{ c.registeredAt | date: 'short' }}</td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: [`
    .head { display:flex; justify-content:space-between; align-items:flex-start; gap:1rem; flex-wrap:wrap; }
    h1 { font-size:1.4rem; font-weight:600; margin:0 0 1rem; color:#0f172a; }
    .filters { display:flex; gap:.35rem; flex-wrap:wrap; }
    .chip { border:1px solid #cbd5e1; background:#fff; color:#475569; border-radius:9999px; padding:.2rem .7rem;
      font-size:.75rem; cursor:pointer; }
    .chip.active { background:#0d9488; color:#fff; border-color:#0d9488; }
    table { width:100%; border-collapse:collapse; margin-top:1rem; font-size:.9rem; background:#fff; border:1px solid #e2e8f0; border-radius:.5rem; overflow:hidden; }
    th, td { text-align:left; padding:.55rem .75rem; border-bottom:1px solid #f1f5f9; }
    th { background:#f8fafc; color:#64748b; font-weight:600; font-size:.75rem; text-transform:uppercase; }
    .mono { font-family:ui-monospace,monospace; }
    .link { color:#0d9488; text-decoration:none; } .link:hover { text-decoration:underline; }
    .muted { color:#94a3b8; } .error { color:#dc2626; }
    .badge { font-size:.7rem; padding:.15rem .5rem; border-radius:9999px; background:#e2e8f0; color:#334155; }
    .badge[data-s="RESERVED"] { background:#fef3c7; color:#92400e; }
    .badge[data-s="SETTLED"] { background:#d1fae5; color:#065f46; }
    .badge[data-s="NOTIFIED"] { background:#dbeafe; color:#1e40af; }
    .badge[data-s="REJECTED"] { background:#fee2e2; color:#991b1b; }
  `],
})
export class InboxComponent {
  private readonly svc = inject(ClaimsService);
  readonly statuses = STATUSES;
  readonly claims = signal<Claim[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly filter = signal<ClaimStatus | 'ALL'>('ALL');

  constructor() {
    this.load();
  }

  setFilter(s: ClaimStatus | 'ALL') {
    this.filter.set(s);
    this.load();
  }

  private load() {
    this.loading.set(true);
    this.error.set(null);
    const status = this.filter() === 'ALL' ? undefined : this.filter();
    this.svc.list(status).subscribe({
      next: (rows) => { this.claims.set(rows); this.loading.set(false); },
      error: (e) => { this.error.set('Failed to load claims: ' + (e?.message ?? e)); this.loading.set(false); },
    });
  }
}
