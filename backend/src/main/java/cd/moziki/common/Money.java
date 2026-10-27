package cd.moziki.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Moziki stores money as integer minor units (centimes) to avoid floating-point
 * errors. Amounts with more than two decimals are rejected, never rounded.
 */
public final class Money {

    public static final int SCALE = 2;

    private Money() {
    }

    public static long toMinor(BigDecimal amount) {
        if (amount == null) {
            throw ApiException.badRequest("AMOUNT_REQUIRED", "error.ledger.invalid_amount");
        }
        if (amount.scale() > SCALE && amount.stripTrailingZeros().scale() > SCALE) {
            throw ApiException.badRequest("AMOUNT_PRECISION", "error.ledger.amount_precision");
        }
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("AMOUNT_INVALID", "error.ledger.invalid_amount");
        }
        try {
            return amount.setScale(SCALE, RoundingMode.UNNECESSARY).movePointRight(SCALE).longValueExact();
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest("AMOUNT_INVALID", "error.ledger.amount_too_large");
        }
    }

    /**
     * Counted cash can legitimately be zero (an empty cash box), unlike a
     * ledger posting. Negative counts are never valid.
     */
    public static long toNonNegativeMinor(BigDecimal amount) {
        if (amount == null) {
            throw ApiException.badRequest("AMOUNT_REQUIRED", "error.ledger.invalid_amount");
        }
        if (amount.signum() < 0) {
            throw ApiException.badRequest("AMOUNT_INVALID", "error.reconciliation.negative_count");
        }
        if (amount.signum() == 0) {
            return 0L;
        }
        return toMinor(amount);
    }

    public static BigDecimal toDecimal(long minor) {
        return BigDecimal.valueOf(minor).movePointLeft(SCALE).setScale(SCALE, RoundingMode.UNNECESSARY);
    }
}
