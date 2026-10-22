package cd.moziki.ledger.dto;

import cd.moziki.ledger.PaymentChannel;
import cd.moziki.ledger.TransactionKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record PostTransactionRequest(
        @NotNull(message = "{validation.kind}")
        TransactionKind kind,

        UUID memberId,

        @NotNull(message = "{validation.amount.positive}")
        @DecimalMin(value = "0.01", message = "{validation.amount.positive}")
        @Digits(integer = 15, fraction = 2, message = "{validation.amount.precision}")
        BigDecimal amount,

        @NotNull(message = "{validation.channel}")
        PaymentChannel channel,

        @NotBlank(message = "{validation.required}")
        @Size(max = 500, message = "{validation.description.length}")
        String description,

        @Size(max = 80)
        String externalRef,

        @Size(max = 80)
        String idempotencyKey
) {
}
