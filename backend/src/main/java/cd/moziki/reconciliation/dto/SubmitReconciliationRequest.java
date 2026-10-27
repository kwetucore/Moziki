package cd.moziki.reconciliation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubmitReconciliationRequest(
        @NotNull(message = "{validation.required}")
        LocalDate businessDate,

        @NotNull(message = "{validation.required}")
        @DecimalMin(value = "0.00", message = "{validation.count.non_negative}")
        @Digits(integer = 15, fraction = 2, message = "{validation.amount.precision}")
        BigDecimal countedCash,

        @NotNull(message = "{validation.required}")
        @DecimalMin(value = "0.00", message = "{validation.count.non_negative}")
        @Digits(integer = 15, fraction = 2, message = "{validation.amount.precision}")
        BigDecimal countedAirtel,

        @NotNull(message = "{validation.required}")
        @DecimalMin(value = "0.00", message = "{validation.count.non_negative}")
        @Digits(integer = 15, fraction = 2, message = "{validation.amount.precision}")
        BigDecimal countedOrange,

        @NotNull(message = "{validation.required}")
        @DecimalMin(value = "0.00", message = "{validation.count.non_negative}")
        @Digits(integer = 15, fraction = 2, message = "{validation.amount.precision}")
        BigDecimal countedMpesa,

        @Size(max = 500, message = "{validation.description.length}")
        String notes
) {
}
