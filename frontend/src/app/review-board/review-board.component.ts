import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';

import { ClaimsService } from '../claims.service';
import { Authority, PaymentRequest, ReserveApproval } from '../models';

/**
 * Large-loss review board (CLM-FIN-03a/b). The reviewer's queue of reserve/payment items held for four-eyes:
 * reserves above authority / large losses await a DIFFERENT, higher-authority approver; payments go
 * request → approve → execute (a non-requester executor disburses). The acting identity (role + user) is
 * passed to the server, which enforces four-eyes + the authority matrix (self-approve → 403, SoD on execute).
 */
@Component({
  selector: 'app-review-board',
  imports: [FormsModule, CurrencyPipe],
  template: `
    <div class="head">
      <h1>Large-loss review board</h1>
      <div class="actor">
        <span class="lbl">Acting as</span>
        <select [(ngModel)]="authority" name="authority">
          <option value="ADJUSTER">ADJUSTER</option>
          <option value="SENIOR">SENIOR</option>
        </select>
        <input [(ngModel)]="user" name="user" placeholder="your user id" />
        <button class="chip" (click)="load()">↻ Refresh</button>
      </div>
    </div>
    <p class="hint">Four-eyes is enforced server-side: you cannot approve your own request, and the approver/
      executor must have authority for the amount (payments also need a non-requester executor).</p>

    @if (msg()) { <p [class]="ok() ? 'ok' : 'error'">{{ msg() }}</p> }

    <section>
      <h2>Reserve approvals <span class="count">{{ reserves().length }}</span></h2>
      @if (reserves().length === 0) { <p class="muted">No reserves awaiting approval.</p> }
      @if (reserves().length > 0) {
        <table>
          <thead><tr><th>Claim</th><th>Amount</th><th>Requested by</th><th>Status</th><th>Action</th></tr></thead>
          <tbody>
            @for (r of reserves(); track r.approvalId) {
              <tr>
                <td class="mono">{{ r.claimNumber }}</td>
                <td class="num">{{ r.amount | currency:'EUR' }}</td>
                <td>{{ r.requestedBy }}</td>
                <td><span class="badge" [attr.data-s]="r.status">{{ r.status }}</span></td>
                <td class="act">
                  <button class="btn" [disabled]="busy()" (click)="approveReserve(r)">Approve</button>
                  <button class="btn ghost" [disabled]="busy()" (click)="rejectReserve(r)">Reject</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>

    <section>
      <h2>Payment requests <span class="count">{{ payments().length }}</span></h2>
      @if (payments().length === 0) { <p class="muted">No payments awaiting approval/execution.</p> }
      @if (payments().length > 0) {
        <table>
          <thead><tr><th>Claim</th><th>Amount</th><th>Requested by</th><th>Approved by</th><th>Status</th><th>Action</th></tr></thead>
          <tbody>
            @for (p of payments(); track p.requestId) {
              <tr>
                <td class="mono">{{ p.claimNumber }}</td>
                <td class="num">{{ p.amount | currency:'EUR' }}</td>
                <td>{{ p.requestedBy }}</td>
                <td>{{ p.approvedBy || '—' }}</td>
                <td><span class="badge" [attr.data-s]="p.status">{{ p.status }}</span></td>
                <td class="act">
                  @if (p.status === 'requested') {
                    <button class="btn" [disabled]="busy()" (click)="approvePayment(p)">Approve</button>
                    <button class="btn ghost" [disabled]="busy()" (click)="rejectPayment(p)">Reject</button>
                  }
                  @if (p.status === 'approved') {
                    <button class="btn exec" [disabled]="busy()" (click)="executePayment(p)">Execute payout</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
  styles: [`
    .head { display:flex; justify-content:space-between; align-items:center; gap:1rem; flex-wrap:wrap; }
    h1 { font-size:1.4rem; font-weight:600; margin:0; color:#0f172a; }
    h2 { font-size:1rem; font-weight:600; color:#334155; margin:1.5rem 0 .5rem; }
    .count { background:#e2e8f0; color:#334155; border-radius:9999px; padding:.05rem .5rem; font-size:.75rem; margin-left:.35rem; }
    .actor { display:flex; gap:.5rem; align-items:center; font-size:.85rem; }
    .lbl { color:#64748b; } .hint { color:#94a3b8; font-size:.8rem; margin:.25rem 0 0; }
    select, input { border:1px solid #cbd5e1; border-radius:.375rem; padding:.3rem .5rem; font-size:.85rem; }
    table { width:100%; border-collapse:collapse; margin-top:.5rem; font-size:.9rem; background:#fff; border:1px solid #e2e8f0; border-radius:.5rem; overflow:hidden; }
    th, td { text-align:left; padding:.55rem .75rem; border-bottom:1px solid #f1f5f9; }
    th { background:#f8fafc; color:#64748b; font-weight:600; font-size:.72rem; text-transform:uppercase; }
    .mono { font-family:ui-monospace,monospace; } .num { font-variant-numeric:tabular-nums; }
    .act { display:flex; gap:.4rem; }
    .btn { background:#0d9488; color:#fff; border:none; padding:.3rem .7rem; border-radius:.375rem; font-size:.8rem; cursor:pointer; }
    .btn:hover:not(:disabled) { background:#0f766e; } .btn:disabled { opacity:.5; cursor:default; }
    .btn.ghost { background:#fff; color:#475569; border:1px solid #cbd5e1; }
    .btn.exec { background:#b45309; } .btn.exec:hover:not(:disabled) { background:#92400e; }
    .chip { border:1px solid #cbd5e1; background:#fff; color:#475569; border-radius:9999px; padding:.2rem .7rem; font-size:.8rem; cursor:pointer; }
    .muted { color:#94a3b8; } .error { color:#dc2626; font-size:.85rem; } .ok { color:#065f46; font-size:.85rem; }
    .badge { font-size:.7rem; padding:.15rem .5rem; border-radius:9999px; background:#e2e8f0; color:#334155; text-transform:uppercase; }
    .badge[data-s="pending"], .badge[data-s="requested"] { background:#fef3c7; color:#92400e; }
    .badge[data-s="approved"] { background:#dbeafe; color:#1e40af; }
    .badge[data-s="executed"] { background:#d1fae5; color:#065f46; }
    .badge[data-s="rejected"] { background:#fee2e2; color:#991b1b; }
  `],
})
export class ReviewBoardComponent {
  private readonly svc = inject(ClaimsService);

  authority: Authority = 'SENIOR';
  user = '';
  readonly reserves = signal<ReserveApproval[]>([]);
  readonly payments = signal<PaymentRequest[]>([]);
  readonly busy = signal(false);
  readonly msg = signal<string | null>(null);
  readonly ok = signal(false);

  constructor() {
    this.load();
  }

  load() {
    this.svc.listReserveApprovals().subscribe({
      next: (rows) => this.reserves.set(rows.filter((r) => r.status === 'pending')),
      error: (e) => this.fail(e),
    });
    this.svc.listPaymentRequests().subscribe({
      next: (rows) => this.payments.set(rows),
      error: (e) => this.fail(e),
    });
  }

  approveReserve(r: ReserveApproval) { this.act(this.svc.approveReserve(r.approvalId, this.authority, this.user), `Reserve ${r.claimNumber} approved`); }
  rejectReserve(r: ReserveApproval) { this.act(this.svc.rejectReserve(r.approvalId, 'rejected at review', this.authority, this.user), `Reserve ${r.claimNumber} rejected`); }
  approvePayment(p: PaymentRequest) { this.act(this.svc.approvePayment(p.requestId, this.authority, this.user), `Payment ${p.claimNumber} approved — awaits execution`); }
  executePayment(p: PaymentRequest) { this.act(this.svc.executePayment(p.requestId, this.authority, this.user), `Payment ${p.claimNumber} executed`); }
  rejectPayment(p: PaymentRequest) { this.act(this.svc.rejectPayment(p.requestId, 'rejected at review', this.authority, this.user), `Payment ${p.claimNumber} rejected`); }

  private act(obs: import('rxjs').Observable<unknown>, okMsg: string) {
    if (!this.user.trim()) { this.ok.set(false); this.msg.set('Enter your user id first (four-eyes needs who you are).'); return; }
    this.busy.set(true);
    this.msg.set(null);
    obs.subscribe({
      next: () => { this.ok.set(true); this.msg.set(okMsg); this.busy.set(false); this.load(); },
      error: (e) => { this.fail(e); this.busy.set(false); },
    });
  }

  private fail(e: any) {
    const detail = e?.error?.detail || e?.error?.message || e?.message || 'request failed';
    this.ok.set(false);
    this.msg.set(`Rejected (${e?.status ?? '?'}): ${detail}`);
  }
}
