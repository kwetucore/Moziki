package cd.moziki.member.dto;

import cd.moziki.user.Role;
import cd.moziki.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID id,
        String memberNumber,
        String fullName,
        String phone,
        String email,
        Role role,
        UserStatus status,
        String locale,
        long savingsMinor,
        long sharesMinor,
        Instant createdAt
) {
}
