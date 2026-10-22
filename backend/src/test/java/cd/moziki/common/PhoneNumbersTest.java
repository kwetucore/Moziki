package cd.moziki.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

    @Test
    void normalizesLocalDrcNumbers() {
        assertThat(PhoneNumbers.normalize("0990000001")).isEqualTo("+243990000001");
        assertThat(PhoneNumbers.normalize("0990 000 001")).isEqualTo("+243990000001");
        assertThat(PhoneNumbers.normalize("+243 990 000 001")).isEqualTo("+243990000001");
        assertThat(PhoneNumbers.normalize("00243990000001")).isEqualTo("+243990000001");
        assertThat(PhoneNumbers.normalize("243990000001")).isEqualTo("+243990000001");
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThatThrownBy(() -> PhoneNumbers.normalize("12345"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("PHONE_INVALID");
        assertThatThrownBy(() -> PhoneNumbers.normalize(null)).isInstanceOf(ApiException.class);
    }

    @Test
    void masksPhoneNumbersForAuditMetadata() {
        assertThat(PhoneNumbers.masked("+243990000001")).isEqualTo("+243****01");
    }
}
