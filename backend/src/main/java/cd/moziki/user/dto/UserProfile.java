package cd.moziki.user.dto;

import cd.moziki.user.Role;
import cd.moziki.user.User;
import cd.moziki.user.UserStatus;

import java.util.UUID;

public record UserProfile(
        UUID id,
        String memberNumber,
        String fullName,
        String phone,
        String email,
        Role role,
        UserStatus status,
        String locale
) {
    public static UserProfile from(User user) {
        return new UserProfile(user.getId(), user.getMemberNumber(), user.getFullName(),
                user.getPhone(), user.getEmail(), user.getRole(), user.getStatus(), user.getLocale());
    }
}
