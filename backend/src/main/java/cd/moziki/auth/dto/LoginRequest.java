package cd.moziki.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "{validation.phone}")
        @Size(max = 24)
        String phone,

        @NotBlank(message = "{error.auth.password_required}")
        @Size(max = 128)
        String password
) {
}
