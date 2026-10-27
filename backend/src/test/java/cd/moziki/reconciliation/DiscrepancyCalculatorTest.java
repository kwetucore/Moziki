package cd.moziki.reconciliation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscrepancyCalculatorTest {

    @Test
    void marksBalancedOnlyWhenEveryBucketMatches() {
        DiscrepancyCalculator.Breakdown result =
                DiscrepancyCalculator.compare(100_00, 100_00, 40_00, 40_00,
                        20_00, 20_00, 5_00, 5_00);

        assertThat(result.status()).isEqualTo(ReconciliationStatus.BALANCED);
        assertThat(result.cashDifferenceMinor()).isZero();
        assertThat(result.airtelDifferenceMinor()).isZero();
        assertThat(result.orangeDifferenceMinor()).isZero();
        assertThat(result.mpesaDifferenceMinor()).isZero();
        assertThat(result.totalDifferenceMinor()).isZero();
        assertThat(result.totalCountedMinor()).isEqualTo(165_00);
        assertThat(result.totalBookMinor()).isEqualTo(165_00);
    }

    @Test
    void reportsShortfallAsNegativeDifference() {
        DiscrepancyCalculator.Breakdown result =
                DiscrepancyCalculator.compare(95_00, 100_00, 0, 0, 0, 0, 0, 0);

        assertThat(result.status()).isEqualTo(ReconciliationStatus.DISCREPANCY);
        assertThat(result.cashDifferenceMinor()).isEqualTo(-5_00);
        assertThat(result.totalDifferenceMinor()).isEqualTo(-5_00);
    }

    @Test
    void reportsOverageAsPositiveDifference() {
        DiscrepancyCalculator.Breakdown result =
                DiscrepancyCalculator.compare(0, 0, 0, 0, 0, 0, 12_50, 10_00);

        assertThat(result.status()).isEqualTo(ReconciliationStatus.DISCREPANCY);
        assertThat(result.mpesaDifferenceMinor()).isEqualTo(2_50);
    }

    @Test
    void offsettingBucketDifferencesAreStillADiscrepancy() {
        // Cash is short by 100 while Airtel is over by 100: the total cancels,
        // but the day is not balanced because each funding source must match.
        DiscrepancyCalculator.Breakdown result =
                DiscrepancyCalculator.compare(900_00, 1_000_00, 100_00, 0, 0, 0, 0, 0);

        assertThat(result.totalDifferenceMinor()).isZero();
        assertThat(result.status()).isEqualTo(ReconciliationStatus.DISCREPANCY);
        assertThat(result.cashDifferenceMinor()).isEqualTo(-100_00);
        assertThat(result.airtelDifferenceMinor()).isEqualTo(100_00);
    }

    @Test
    void emptyCashBoxWithZeroBookBalanceIsBalanced() {
        DiscrepancyCalculator.Breakdown result =
                DiscrepancyCalculator.compare(0, 0, 0, 0, 0, 0, 0, 0);

        assertThat(result.status()).isEqualTo(ReconciliationStatus.BALANCED);
        assertThat(result.totalCountedMinor()).isZero();
    }
}
