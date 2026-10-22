package cd.moziki.dashboard.dto;

import cd.moziki.common.PageResponse;
import cd.moziki.cooperative.dto.CooperativeSummary;
import cd.moziki.ledger.dto.TransactionResponse;

import java.time.Instant;

public record DashboardResponse(
        CooperativeSummary cooperative,
        PoolTotals pool,
        PersonalTotals personal,
        long memberCount,
        long transactionCount,
        Instant lastTransactionAt,
        PageResponse<TransactionResponse> recentTransactions
) {
    public record PoolTotals(
            long cashMinor,
            long mobileMoneyMinor,
            long savingsMinor,
            long sharesMinor,
            long loansOutstandingMinor,
            long feeIncomeMinor
    ) {
    }

    public record PersonalTotals(long savingsMinor, long sharesMinor) {
    }
}
