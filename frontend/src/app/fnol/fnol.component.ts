import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ClaimsService } from '../claims.service';
import { FnolRequest } from '../models';

@Component({
  selector: 'app-fnol',
  imports: [FormsModule],
  template: `
    <h1>New claim (FNOL)</h1>
    <form class="card" (ngSubmit)="submit()">
      <label>Policy number
        <input name="policyNumber" [(ngModel)]="form.policyNumber" required placeholder="POL-PROP-0001" />
      </label>
      <label>Loss date
        <input name="lossDate" type="date" [(ngModel)]="form.lossDate" required />
      </label>
      <label>Peril
        <input name="peril" [(ngModel)]="form.peril" required placeholder="FIRE" />
      </label>
      <label>Claimant name
        <input name="claimantName" [(ngModel)]="form.claimantName" required maxlength="160" />
      </label>
      <label>Description
        <textarea name="description" [(ngModel)]="form.description" rows="3" maxlength="2000"></textarea>
      </label>
      @if (error()) { <p class="error">{{ error() }}</p> }
      <button class="btn" type="submit" [disabled]="submitting()">
        {{ submitting() ? 'Registering…' : 'Register claim' }}
      </button>
    </form>
  `,
  styles: [`
    h1 { font-size:1.4rem; font-weight:600; margin:0 0 1rem; color:#0f172a; }
    .card { background:#fff; border:1px solid #e2e8f0; border-radius:.5rem; padding:1.25rem; max-width:32rem; display:flex; flex-direction:column; gap:.9rem; }
    label { display:flex; flex-direction:column; gap:.3rem; font-size:.85rem; color:#475569; }
    input, textarea { border:1px solid #cbd5e1; border-radius:.375rem; padding:.5rem .6rem; font-size:.9rem; font-family:inherit; }
    .btn { background:#0d9488; color:#fff; border:0; border-radius:.375rem; padding:.6rem 1rem; font-weight:500; cursor:pointer; align-self:flex-start; }
    .btn:disabled { opacity:.6; cursor:default; }
    .error { color:#dc2626; font-size:.85rem; }
  `],
})
export class FnolComponent {
  private readonly svc = inject(ClaimsService);
  private readonly router = inject(Router);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  form: FnolRequest = { policyNumber: '', lossDate: '', peril: '', claimantName: '', description: '' };

  submit() {
    this.submitting.set(true);
    this.error.set(null);
    this.svc.fnol(this.form).subscribe({
      next: (claim) => this.router.navigate(['/claims', claim.claimNumber]),
      error: (e) => {
        const detail = e?.error?.detail || e?.error?.message || e?.message || 'request failed';
        this.error.set(`Could not register the claim (${e?.status ?? '?'}): ${detail}`);
        this.submitting.set(false);
      },
    });
  }
}
