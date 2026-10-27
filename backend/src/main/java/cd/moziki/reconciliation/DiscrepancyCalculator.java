package cd.moziki.reconciliation;

/**
 * Pure discrepancy logic: declared count minus the journal snapshot. A day is
 * BALANCED only when every bucket matches exactly; two offsetting differences
 * must never cancel each other into a false "balanced" result.
 */
public final class DiscrepancyCalculator {

    private DiscrepancyCalculator() {
    }

    public record Breakdown(
            long cashDifferenceMinor,
            long airtelDifferenceMinor,
            long orangeDifferenceMinor,
            long mpesaDifferenceMinor,
            long totalCountedMinor,
            long totalBookMinor,
            long totalDifferenceMinor,
            ReconciliationStatus status
    ) {
    }

    public static Breakdown compare(long countedCashMinor, long bookCashMinor,
                                    long countedAirtelMinor, long bookAirtelMinor,
                                    long countedOrangeMinor, long bookOrangeMinor,
                                    long countedMpesaMinor, long bookMpesaMinor) {
        long cashDifference = Math.subtractExact(countedCashMinor, bookCashMinor);
        long airtelDifference = Math.subtractExact(countedAirtelMinor, bookAirtelMinor);
        long orangeDifference = Math.subtractExact(countedOrangeMinor, bookOrangeMinor);
        long mpesaDifference = Math.subtractExact(countedMpesaMinor, bookMpesaMinor);
        long totalCounted = Math.addExact(
                Math.addExact(countedCashMinor, countedAirtelMinor),
                Math.addExact(countedOrangeMinor, countedMpesaMinor));
        long totalBook = Math.addExact(
                Math.addExact(bookCashMinor, bookAirtelMinor),
                Math.addExact(bookOrangeMinor, bookMpesaMinor));
        long totalDifference = Math.subtractExact(totalCounted, totalBook);

        boolean allBucketsMatch = cashDifference == 0
                && airtelDifference == 0
                && orangeDifference == 0
                && mpesaDifference == 0;

        return new Breakdown(cashDifference, airtelDifference, orangeDifference, mpesaDifference,
                totalCounted, totalBook, totalDifference,
                allBucketsMatch ? ReconciliationStatus.BALANCED : ReconciliationStatus.DISCREPANCY);
    }
}
