package cd.moziki.reconciliation.dto;

import cd.moziki.reconciliation.ReconciliationDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewDecisionRequest(
        @NotNull(message = "{validation.required}")
        ReconciliationDecision decision,

        @Size(max = 500, message = "{validation.description.length}")
        String note
) {
}
