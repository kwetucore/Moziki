package cd.moziki.member.dto;

import cd.moziki.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateMemberRequest(
        @NotBlank(message = "{validation.required}")
        @Size(min = 3, max = 160, message = "{validation.required}")
        String fullName,

        @NotBlank(message = "{validation.phone}")
        @Size(max = 24)
        String phone,

        @Email(message = "{validation.required}")
        @Size(max = 190)
        String email,

        @NotBlank(message = "{validation.password.length}")
        @Size(min = 8, max = 72, message = "{validation.password.length}")
        String password,

        Role role,

        @Pattern(regexp = "en|fr|sw", message = "{validation.role}")
        String locale
) {
}
