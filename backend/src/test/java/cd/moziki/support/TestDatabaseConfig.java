package cd.moziki.support;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The integration suite runs against a dedicated database (moziki_test). It is
 * cleaned and re-migrated so every run exercises the real Flyway SQL, including
 * the double-entry constraint trigger.
 */
@TestConfiguration
public class TestDatabaseConfig {

    @Bean
    public FlywayMigrationStrategy cleanMigrateStrategy() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
