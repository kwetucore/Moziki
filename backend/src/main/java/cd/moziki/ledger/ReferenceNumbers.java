package cd.moziki.ledger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class ReferenceNumbers {

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    private final JdbcTemplate jdbcTemplate;

    public ReferenceNumbers(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** e.g. MOZ-2026-000042 */
    public String nextTransactionReference() {
        Long sequence = jdbcTemplate.queryForObject(
                "select nextval('transaction_reference_seq')", Long.class);
        return "MOZ-%d-%06d".formatted(LocalDate.now(KINSHASA).getYear(), sequence);
    }

    /** e.g. M-00007 */
    public String nextMemberNumber() {
        Long sequence = jdbcTemplate.queryForObject(
                "select nextval('member_number_seq')", Long.class);
        return "M-%05d".formatted(sequence);
    }
}
