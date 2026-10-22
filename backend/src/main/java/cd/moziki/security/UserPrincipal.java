package cd.moziki.security;

import cd.moziki.user.Role;
import cd.moziki.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final UUID cooperativeId;
    private final String memberNumber;
    private final String fullName;
    private final String phone;
    private final String passwordHash;
    private final Role role;
    private final String locale;
    private final boolean active;

    public UserPrincipal(UUID id, UUID cooperativeId, String memberNumber, String fullName, String phone,
                         String passwordHash, Role role, String locale, boolean active) {
        this.id = id;
        this.cooperativeId = cooperativeId;
        this.memberNumber = memberNumber;
        this.fullName = fullName;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role;
        this.locale = locale;
        this.active = active;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getCooperativeId(), user.getMemberNumber(),
                user.getFullName(), user.getPhone(), user.getPasswordHash(), user.getRole(),
                user.getLocale(), user.isActive());
    }

    public UUID getId() {
        return id;
    }

    public UUID getCooperativeId() {
        return cooperativeId;
    }

    public String getMemberNumber() {
        return memberNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public Role getRole() {
        return role;
    }

    public String getLocale() {
        return locale;
    }

    public boolean isLeader() {
        return role == Role.LEADER;
    }

    public boolean isAuditor() {
        return role == Role.AUDITOR;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return phone;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
