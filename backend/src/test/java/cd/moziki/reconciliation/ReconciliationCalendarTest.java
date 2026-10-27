package cd.moziki.reconciliation;

import cd.moziki.common.ApiException;
import cd.moziki.cooperative.Cooperative;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconciliationCalendarTest {

    private static final String TIMEZONE = "Africa/Kinshasa";
    private static final Cooperative COOP = Cooperative.builder()
            .code("CAL")
            .name("Calendar test")
            .currency("CDF")
            .timezone(TIMEZONE)
            .build();

    @Test
    void previousDayIsOpenDuringTheGraceWindow() {
        ReconciliationCalendar calendar = calendarAt(7, 12, "2026-10-22T07:00:00Z");

        assertThat(calendar.today(COOP)).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(calendar.earliestAllowed(COOP)).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(calendar.isLocked(COOP, LocalDate.of(2026, 10, 21))).isFalse();
    }

    @Test
    void previousDayLocksAutomaticallyAtTheCutoff() {
        ReconciliationCalendar calendar = calendarAt(7, 12, "2026-10-22T11:00:00Z");

        // 11:00 UTC is 12:00 in Kinshasa: the grace window has just closed.
        assertThat(calendar.earliestAllowed(COOP)).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(calendar.isLocked(COOP, LocalDate.of(2026, 10, 21))).isTrue();
        assertThatThrownBy(() -> calendar.validateBusinessDate(
                COOP, LocalDate.of(2026, 10, 21)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("error.reconciliation.day_locked");
    }

    @Test
    void todayIsAlwaysOpenAndFutureDatesAreRejected() {
        // 22:00 UTC is 23:00 in Kinshasa: the last hour of the local day, and
        // with lock-hour 0 the previous day is already locked.
        ReconciliationCalendar calendar = calendarAt(7, 0, "2026-10-22T22:00:00Z");

        assertThat(calendar.today(COOP)).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(calendar.isLocked(COOP, LocalDate.of(2026, 10, 21))).isTrue();
        assertThat(calendar.validateBusinessDate(COOP, LocalDate.of(2026, 10, 22)))
                .isEqualTo(LocalDate.of(2026, 10, 22));
        assertThatThrownBy(() -> calendar.validateBusinessDate(
                COOP, LocalDate.of(2026, 10, 23)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("error.reconciliation.future_date");
    }

    @Test
    void backfillCeilingCanOnlyBeStricterThanTheLock() {
        ReconciliationCalendar strict = calendarAt(0, 12, "2026-10-22T07:00:00Z");

        assertThat(strict.earliestAllowed(COOP)).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(strict.isLocked(COOP, LocalDate.of(2026, 10, 21))).isTrue();
    }

    private static ReconciliationCalendar calendarAt(int backfillDays, int lockHour, String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new ReconciliationCalendar(backfillDays, lockHour, clock);
    }
}
