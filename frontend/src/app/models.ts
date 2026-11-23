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

// CLM-FIN-03a/b — the four-eyes review queues (the large-loss review board).
// A reserve/settle above authority or a large loss comes back as this handle (HTTP 202) instead of a Claim.
export interface ApprovalHandle {
  status: 'PENDING_APPROVAL';
  approvalId?: string; // reserve
  requestId?: string;  // payment
}

export interface ReserveApproval {
  approvalId: string;
  claimNumber: string;
  amount: number;
  requestedBy: string;
  status: string; // pending | approved | rejected
}

export interface PaymentRequest {
  requestId: string;
  claimNumber: string;
  amount: number;
  requestedBy: string;
  status: string; // requested | approved | executed | rejected
  approvedBy?: string | null;
}

export function isPendingApproval(x: Claim | ApprovalHandle): x is ApprovalHandle {
  return (x as ApprovalHandle).status === 'PENDING_APPROVAL';
}
