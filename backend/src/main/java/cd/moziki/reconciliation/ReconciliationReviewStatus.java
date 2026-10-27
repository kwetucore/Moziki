package cd.moziki.reconciliation;

/**
 * Derived state of a cash count report:
 * <ul>
 *   <li>the first submission for a day is trusted immediately ({@code APPROVED});</li>
 *   <li>a correction stays {@code PENDING} until an auditor decides;</li>
 *   <li>a rejected correction never becomes the authoritative count.</li>
 * </ul>
 */
public enum ReconciliationReviewStatus {
    PENDING,
    APPROVED,
    REJECTED
}
