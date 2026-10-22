package cd.moziki.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReversalRequest(
        @NotBlank(message = "{validation.required}")
        @Size(max = 300, message = "{validation.description.length}")
        String reason
) {
}
