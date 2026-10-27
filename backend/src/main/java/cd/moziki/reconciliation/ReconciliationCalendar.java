package cd.moziki.reconciliation;

import cd.moziki.common.ApiException;
import cd.moziki.cooperative.Cooperative;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Business dates follow the cooperative's timezone, not the server's.
 *
 * <p>Today is always open. A completed day stays open until {@code lock-hour}
 * on the following local morning, then locks automatically: late counts get a
 * grace window, but a settled day can no longer be rewritten.</p>
 */
@Component
public class ReconciliationCalendar {

    private final int maxBackfillDays;
    private final int lockHour;
    private final Clock clock;

    public ReconciliationCalendar(
            @Value("${moziki.reconciliation.max-backfill-days:7}") int maxBackfillDays,
            @Value("${moziki.reconciliation.lock-hour:12}") int lockHour,
            Clock clock) {
        this.maxBackfillDays = Math.max(0, maxBackfillDays);
        this.lockHour = Math.min(Math.max(lockHour, 0), 23);
        this.clock = clock;
    }

    public LocalDate today(Cooperative cooperative) {
        return LocalDate.now(clock.withZone(zone(cooperative)));
    }

    public LocalTime localTime(Cooperative cooperative) {
        return LocalTime.now(clock.withZone(zone(cooperative)));
    }

    public int lockHour() {
        return lockHour;
    }

    /**
     * The oldest date still open: yesterday during the grace window, otherwise
     * today. {@code max-backfill-days} can only make this stricter, never
     * reopen a locked day.
     */
    public LocalDate earliestAllowed(Cooperative cooperative) {
        LocalDate today = today(cooperative);
        LocalDate lockBoundary = localTime(cooperative).getHour() < lockHour
                ? today.minusDays(1)
                : today;
        LocalDate backfillBoundary = today.minusDays(maxBackfillDays);
        return lockBoundary.isAfter(backfillBoundary) ? lockBoundary : backfillBoundary;
    }

    public boolean isLocked(Cooperative cooperative, LocalDate businessDate) {
        return businessDate.isBefore(earliestAllowed(cooperative));
    }

    public LocalDate validateBusinessDate(Cooperative cooperative, LocalDate businessDate) {
        if (businessDate == null) {
            throw ApiException.badRequest("BUSINESS_DATE_REQUIRED", "validation.required");
        }
        LocalDate today = today(cooperative);
        if (businessDate.isAfter(today)) {
            throw ApiException.unprocessable("RECONCILIATION_FUTURE_DATE",
                    "error.reconciliation.future_date");
        }
        if (isLocked(cooperative, businessDate)) {
            throw ApiException.unprocessable("RECONCILIATION_DAY_LOCKED",
                    "error.reconciliation.day_locked", lockHour);
        }
        return businessDate;
    }

    private static ZoneId zone(Cooperative cooperative) {
        return ZoneId.of(cooperative.getTimezone());
    }
}
