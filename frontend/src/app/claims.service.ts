import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Authority, Claim, FnolRequest } from './models';

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

  reserve(claimNumber: string, amount: number, authority: Authority, user: string): Observable<Claim> {
    return this.http.post<Claim>(`${BASE}/${encodeURIComponent(claimNumber)}/reserve`, { amount }, {
      headers: this.authHeaders(authority, user),
    });
  }

  settle(claimNumber: string, amount: number, authority: Authority, user: string): Observable<Claim> {
    return this.http.post<Claim>(`${BASE}/${encodeURIComponent(claimNumber)}/settle`, { amount }, {
      headers: this.authHeaders(authority, user),
    });
  }

  private authHeaders(authority: Authority, user: string): HttpHeaders {
    // The acting authority is enforced SERVER-SIDE by the ACL (authority matrix). Real identity =
    // Authentik OIDC later; for now the workbench passes the selected role + user explicitly.
    return new HttpHeaders({ 'X-Claims-Authority': authority, 'X-Claims-User': user || 'workbench' });
  }
}
