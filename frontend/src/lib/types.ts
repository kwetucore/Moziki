export type Role = 'MEMBER' | 'LEADER' | 'AUDITOR';
export type UserStatus = 'ACTIVE' | 'SUSPENDED';
export type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'INCOME' | 'EXPENSE';
export type AccountCategory =
  | 'CASH'
  | 'MOBILE_MONEY'
  | 'SAVINGS'
  | 'SHARES'
  | 'LOANS'
  | 'FEES'
  | 'INTEREST'
  | 'OTHER';
export type WalletProvider = 'AIRTEL' | 'ORANGE' | 'MPESA' | null;
export type TransactionKind =
  | 'DEPOSIT'
  | 'WITHDRAWAL'
  | 'SHARE_PURCHASE'
  | 'LOAN_DISBURSEMENT'
  | 'LOAN_REPAYMENT'
  | 'FEE'
  | 'REVERSAL'
  | 'ADJUSTMENT';
export type PaymentChannel = 'CASH' | 'AIRTEL_MONEY' | 'ORANGE_MONEY' | 'MPESA' | 'BANK' | 'INTERNAL';
export type EntryDirection = 'DEBIT' | 'CREDIT';
export type TransactionStatus = 'POSTED' | 'REVERSED';
export type TransactionSource = 'WEB' | 'USSD' | 'SYSTEM';
export type ReconciliationStatus = 'BALANCED' | 'DISCREPANCY';
export type ReconciliationReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type ReconciliationDecision = 'APPROVED' | 'REJECTED';

export interface UserProfile {
  id: string;
  memberNumber: string;
  fullName: string;
  phone: string;
  email?: string | null;
  role: Role;
  status: UserStatus;
  locale: string;
}

export interface Cooperative {
  id: string;
  code: string;
  name: string;
  country: string;
  currency: string;
  defaultLocale: string;
}

export interface AuthResponse {
  token: string;
  expiresInSeconds: number;
  user: UserProfile;
  cooperative: Cooperative;
}

export interface MeResponse {
  user: UserProfile;
  cooperative: Cooperative;
}

export interface Account {
  id: string;
  code: string;
  name: string;
  type: AccountType;
  category: AccountCategory;
  walletProvider: WalletProvider;
  currency: string;
  balanceMinor: number;
  displayBalanceMinor: number;
  memberId?: string | null;
  memberName?: string | null;
  systemAccount: boolean;
}

export interface LedgerEntry {
  id: string;
  accountId: string;
  accountCode?: string | null;
  accountName?: string | null;
  direction: EntryDirection;
  amountMinor: number;
}

export interface Transaction {
  id: string;
  reference: string;
  kind: TransactionKind;
  channel: PaymentChannel;
  source: TransactionSource;
  status: TransactionStatus;
  amountMinor: number;
  currency: string;
  description: string;
  externalRef?: string | null;
  memberId?: string | null;
  memberName?: string | null;
  createdBy?: string | null;
  createdByName?: string | null;
  reversesTransactionId?: string | null;
  reversalReason?: string | null;
  reversedAt?: string | null;
  createdAt: string;
  entries?: LedgerEntry[] | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Member {
  id: string;
  memberNumber: string;
  fullName: string;
  phone: string;
  email?: string | null;
  role: Role;
  status: UserStatus;
  locale: string;
  savingsMinor: number;
  sharesMinor: number;
  createdAt: string;
}

export interface DashboardPool {
  cashMinor: number;
  mobileMoneyMinor: number;
  savingsMinor: number;
  sharesMinor: number;
  loansOutstandingMinor: number;
  feeIncomeMinor: number;
}

export interface Dashboard {
  cooperative: Cooperative;
  pool: DashboardPool;
  personal: { savingsMinor: number; sharesMinor: number };
  memberCount: number;
  transactionCount: number;
  lastTransactionAt?: string | null;
  recentTransactions: PageResponse<Transaction>;
}

export interface AuditEntry {
  id: string;
  actorId?: string | null;
  actorName?: string | null;
  action: string;
  entityType: string;
  entityId?: string | null;
  summary: string;
  metadata: Record<string, unknown>;
  ipAddress?: string | null;
  createdAt: string;
}

export interface ReconciliationRow {
  accountId: string;
  code: string;
  name: string;
  cachedMinor: number;
  ledgerMinor: number;
  differenceMinor: number;
  consistent: boolean;
}

export interface ReconciliationEvidence {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  uploadedById?: string | null;
  uploadedByName?: string | null;
  createdAt: string;
}

export interface CashReconciliation {
  id: string;
  businessDate: string;
  revision: number;
  supersedesId?: string | null;
  reportedById: string;
  reportedByName?: string | null;
  currency: string;
  status: ReconciliationStatus;
  countedCashMinor: number;
  countedAirtelMinor: number;
  countedOrangeMinor: number;
  countedMpesaMinor: number;
  bookCashMinor: number;
  bookAirtelMinor: number;
  bookOrangeMinor: number;
  bookMpesaMinor: number;
  cashDifferenceMinor: number;
  airtelDifferenceMinor: number;
  orangeDifferenceMinor: number;
  mpesaDifferenceMinor: number;
  totalCountedMinor: number;
  totalBookMinor: number;
  totalDifferenceMinor: number;
  notes?: string | null;
  createdAt: string;
  reviewStatus: ReconciliationReviewStatus;
  reviewedById?: string | null;
  reviewedByName?: string | null;
  reviewedAt?: string | null;
  reviewNote?: string | null;
  evidence: ReconciliationEvidence[];
}

export interface ReconciliationSnapshot {
  id: string;
  businessDate: string;
  revision: number;
  status: ReconciliationStatus;
  totalCountedMinor: number;
  totalBookMinor: number;
  totalDifferenceMinor: number;
  currency: string;
  reportedByName?: string | null;
  createdAt: string;
  reviewStatus: ReconciliationReviewStatus;
  reviewedByName?: string | null;
  reviewedAt?: string | null;
  reviewNote?: string | null;
  evidence: ReconciliationEvidence[];
}

export interface TransparencyEvent {
  id: string;
  action: string;
  actorName?: string | null;
  summary: string;
  createdAt: string;
}

export interface TransparencySummary {
  today: string;
  earliestBusinessDate: string;
  lockHour: number;
  submittedToday: boolean;
  pendingReviewCount: number;
  ledger: {
    cashMinor: number;
    airtelMinor: number;
    orangeMinor: number;
    mpesaMinor: number;
  };
  latestReconciliation: ReconciliationSnapshot | null;
  recentReconciliations: ReconciliationSnapshot[];
  reconciledDays: number;
  balancedDays: number;
  discrepancyDays: number;
  absoluteDifferenceMinor: number;
  auditEvents: number;
  transactionsPosted: number;
  reversals: number;
  recentEvents: TransparencyEvent[];
  beforeLock: boolean;
}

export interface SubmitReconciliationPayload {
  businessDate: string;
  countedCash: string;
  countedAirtel: string;
  countedOrange: string;
  countedMpesa: string;
  notes?: string | null;
}

export interface ReviewReconciliationPayload {
  decision: ReconciliationDecision;
  note?: string | null;
}

export interface PostTransactionPayload {
  kind: TransactionKind;
  memberId?: string | null;
  amount: string;
  channel: PaymentChannel;
  description: string;
  externalRef?: string | null;
  idempotencyKey: string;
}

export interface CreateMemberPayload {
  fullName: string;
  phone: string;
  email?: string | null;
  password: string;
  role: Role;
  locale: string;
}
