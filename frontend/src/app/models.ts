// The claim as the ACL API returns it (GET /api/claims, GET /api/claims/{id}).
export interface Claim {
  claimNumber: string;
  policyNumber: string;
  status: ClaimStatus;
  lossDate: string; // ISO date
  peril: string;
  claimantName: string;
  registeredAt: string; // ISO instant
}

export type ClaimStatus =
  | 'NOTIFIED'
  | 'UNDER_REVIEW'
  | 'RESERVED'
  | 'SETTLED'
  | 'CLOSED'
  | 'REJECTED';

export type Authority = 'ADJUSTER' | 'SENIOR';

export interface FnolRequest {
  policyNumber: string;
  lossDate: string;
  peril: string;
  claimantName: string;
  description?: string;
}
