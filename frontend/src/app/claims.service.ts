import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApprovalHandle, Authority, Claim, FnolRequest, PaymentRequest, ReserveApproval } from './models';

// Same-origin API base. The browser calls /api/claims on THIS origin (nginx), which reverse-proxies
// to the internal ACL Service (the BFF shim, ADR-009) — the ACL API is never exposed to the browser.
const BASE = '/api/claims';

@Injectable({ providedIn: 'root' })
export class ClaimsService {
  private readonly http = inject(HttpClient);

  /** Inbox — newest-first, optional status filter. */
  list(status?: string): Observable<Claim[]> {
    const url = status ? `${BASE}?status=${encodeURIComponent(status)}` : BASE;
    return this.http.get<Claim[]>(url);
  }

  get(claimNumber: string): Observable<Claim> {
    return this.http.get<Claim>(`${BASE}/${encodeURIComponent(claimNumber)}`);
  }

  /** FNOL — register a claim. Idempotency-Key makes a retry create exactly one claim. */
  fnol(req: FnolRequest): Observable<Claim> {
    const headers = new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID() });
    return this.http.post<Claim>(BASE, req, { headers });
  }

  // Reserve/settle now return a Claim (200, applied directly) OR an ApprovalHandle (202, held for four-eyes
  // — above authority or a large loss). The caller branches with isPendingApproval().
  reserve(claimNumber: string, amount: number, authority: Authority, user: string): Observable<Claim | ApprovalHandle> {
    return this.http.post<Claim | ApprovalHandle>(`${BASE}/${encodeURIComponent(claimNumber)}/reserve`, { amount }, {
      headers: this.authHeaders(authority, user),
    });
  }

  settle(claimNumber: string, amount: number, authority: Authority, user: string): Observable<Claim | ApprovalHandle> {
    return this.http.post<Claim | ApprovalHandle>(`${BASE}/${encodeURIComponent(claimNumber)}/settle`, { amount }, {
      headers: this.authHeaders(authority, user),
    });
  }

  // --- Large-loss review board (CLM-FIN-03a/b four-eyes) ---

  listReserveApprovals(): Observable<ReserveApproval[]> {
    return this.http.get<ReserveApproval[]>(`${BASE}/reserve-approvals`);
  }

  /** Approve a pending reserve (a DIFFERENT, higher-authority person) → applies it. Returns the claim. */
  approveReserve(id: string, authority: Authority, user: string): Observable<Claim> {
    return this.http.post<Claim>(`${BASE}/reserve-approvals/${id}/approve`, {}, { headers: this.authHeaders(authority, user) });
  }

  rejectReserve(id: string, reason: string, authority: Authority, user: string): Observable<void> {
    return this.http.post<void>(`${BASE}/reserve-approvals/${id}/reject`, { reason }, { headers: this.authHeaders(authority, user) });
  }

  listPaymentRequests(): Observable<PaymentRequest[]> {
    return this.http.get<PaymentRequest[]>(`${BASE}/payment-requests`);
  }

  /** Approve a requested payment (four-eyes). Does NOT pay — execution is a separate step. */
  approvePayment(id: string, authority: Authority, user: string): Observable<void> {
    return this.http.post<void>(`${BASE}/payment-requests/${id}/approve`, {}, { headers: this.authHeaders(authority, user) });
  }

  /** Execute an approved payment (an authorised, NON-requester executor) → the disbursement. Returns the claim. */
  executePayment(id: string, authority: Authority, user: string): Observable<Claim> {
    return this.http.post<Claim>(`${BASE}/payment-requests/${id}/execute`, {}, { headers: this.authHeaders(authority, user) });
  }

  rejectPayment(id: string, reason: string, authority: Authority, user: string): Observable<void> {
    return this.http.post<void>(`${BASE}/payment-requests/${id}/reject`, { reason }, { headers: this.authHeaders(authority, user) });
  }

  private authHeaders(authority: Authority, user: string): HttpHeaders {
    // The acting authority is enforced SERVER-SIDE by the ACL (authority matrix). Real identity =
    // Authentik OIDC later; for now the workbench passes the selected role + user explicitly.
    return new HttpHeaders({ 'X-Claims-Authority': authority, 'X-Claims-User': user || 'workbench' });
  }
}
