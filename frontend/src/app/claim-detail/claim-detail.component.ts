import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';

import { ClaimsService } from '../claims.service';
import { Authority, Claim } from '../models';

@Component({
  selector: 'app-claim-detail',
  imports: [FormsModule, RouterLink, DatePipe],
  template: `
    <a routerLink="/" class="back">← Inbox</a>

    @if (error()) { <p class="error">{{ error() }}</p> }
    @if (loading()) { <p class="muted">Loading…</p> }

    @if (claim(); as c) {
      <div class="grid">
        <section class="card">
          <div class="row"><h1 class="mono">{{ c.claimNumber }}</h1>
            <span class="badge" [attr.data-s]="c.status">{{ c.status }}</span></div>
          <dl>
            <dt>Policy</dt><dd class="mono">{{ c.policyNumber }}</dd>
            <dt>Peril</dt><dd>{{ c.peril }}</dd>
            <dt>Claimant</dt><dd>{{ c.claimantName }}</dd>
            <dt>Loss date</dt><dd>{{ c.lossDate }}</dd>
            <dt>Registered</dt><dd>{{ c.registeredAt | date: 'medium' }}</dd>
          </dl>
        </section>

        <section class="card">
          <h2>Handle claim</h2>
          <label class="inline">Acting as
            <select [(ngModel)]="authority" name="authority">
              <option value="ADJUSTER">ADJUSTER</option>
              <option value="SENIOR">SENIOR</option>
            </select>
          </label>
          <div class="action">
            <input type="number" min="0" step="0.01" [(ngModel)]="reserveAmount" name="reserveAmount" placeholder="Reserve €" />
            <button class="btn" (click)="doReserve()" [disabled]="busy()">Set reserve</button>
          </div>
          <div class="action">
            <input type="number" min="0" step="0.01" [(ngModel)]="settleAmount" name="settleAmount" placeholder="Settle €" />
            <button class="btn" (click)="doSettle()" [disabled]="busy()">Settle</button>
          </div>
          @if (actionMsg()) { <p [class]="actionOk() ? 'ok' : 'error'">{{ actionMsg() }}</p> }
          <p class="hint">Authority limits are enforced server-side (ADJUSTER ≤ €50k reserve / €10k settle;
            SENIOR ≤ €1M / €500k). Above-authority → 403; out-of-order → 409.</p>
        </section>
      </div>
    }
  `,
  styles: [`
    .back { color:#0d9488; text-decoration:none; font-size:.85rem; } .back:hover { text-decoration:underline; }
    .grid { display:grid; grid-template-columns:1fr 1fr; gap:1rem; margin-top:1rem; }
    @media (max-width:720px){ .grid{ grid-template-columns:1fr; } }
    .card { background:#fff; border:1px solid #e2e8f0; border-radius:.5rem; padding:1.25rem; }
    .row { display:flex; justify-content:space-between; align-items:center; }
    h1 { font-size:1.15rem; margin:0; color:#0f172a; } h2 { font-size:1rem; margin:0 0 .8rem; color:#0f172a; }
    .mono { font-family:ui-monospace,monospace; }
    dl { display:grid; grid-template-columns:auto 1fr; gap:.4rem 1rem; margin:1rem 0 0; font-size:.9rem; }
    dt { color:#64748b; } dd { margin:0; color:#0f172a; }
    .inline { display:flex; align-items:center; gap:.5rem; font-size:.85rem; color:#475569; margin-bottom:.8rem; }
    .action { display:flex; gap:.5rem; margin-bottom:.6rem; }
    input, select { border:1px solid #cbd5e1; border-radius:.375rem; padding:.45rem .6rem; font-size:.9rem; }
    input { flex:1; }
    .btn { background:#0d9488; color:#fff; border:0; border-radius:.375rem; padding:.45rem .9rem; font-weight:500; cursor:pointer; }
    .btn:disabled { opacity:.6; cursor:default; }
    .badge { font-size:.7rem; padding:.15rem .5rem; border-radius:9999px; background:#e2e8f0; color:#334155; }
    .badge[data-s="RESERVED"] { background:#fef3c7; color:#92400e; }
    .badge[data-s="SETTLED"] { background:#d1fae5; color:#065f46; }
    .badge[data-s="NOTIFIED"] { background:#dbeafe; color:#1e40af; }
    .muted { color:#94a3b8; } .error { color:#dc2626; font-size:.85rem; } .ok { color:#065f46; font-size:.85rem; }
    .hint { color:#94a3b8; font-size:.75rem; margin-top:.8rem; }
  `],
})
export class ClaimDetailComponent {
  private readonly svc = inject(ClaimsService);
  private readonly route = inject(ActivatedRoute);

  readonly claim = signal<Claim | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly actionMsg = signal<string | null>(null);
  readonly actionOk = signal(false);

  authority: Authority = 'ADJUSTER';
  reserveAmount: number | null = null;
  settleAmount: number | null = null;

  private readonly id = computed(() => this.route.snapshot.paramMap.get('id') ?? '');

  constructor() {
    this.load();
  }

  private load() {
    this.loading.set(true);
    this.svc.get(this.id()).subscribe({
      next: (c) => { this.claim.set(c); this.loading.set(false); },
      error: (e) => { this.error.set('Failed to load claim: ' + (e?.message ?? e)); this.loading.set(false); },
    });
  }

  doReserve() {
    if (this.reserveAmount == null) return;
    this.run(this.svc.reserve(this.id(), this.reserveAmount, this.authority, 'workbench'), 'Reserve set');
  }

  doSettle() {
    if (this.settleAmount == null) return;
    this.run(this.svc.settle(this.id(), this.settleAmount, this.authority, 'workbench'), 'Claim settled');
  }

  private run(obs: import('rxjs').Observable<Claim>, okMsg: string) {
    this.busy.set(true);
    this.actionMsg.set(null);
    obs.subscribe({
      next: (c) => { this.claim.set(c); this.actionOk.set(true); this.actionMsg.set(okMsg); this.busy.set(false); },
      error: (e) => {
        const detail = e?.error?.detail || e?.error?.message || e?.message || 'request failed';
        this.actionOk.set(false);
        this.actionMsg.set(`Rejected (${e?.status ?? '?'}): ${detail}`);
        this.busy.set(false);
      },
    });
  }
}
