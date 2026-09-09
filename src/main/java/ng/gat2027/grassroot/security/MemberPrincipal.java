package ng.gat2027.grassroot.security;

import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.domain.MemberStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class MemberPrincipal implements UserDetails {
    private final Long id;
    private final String phone;
    private final String passwordHash;
    private final String role;
    private final boolean active;

    public MemberPrincipal(Member m) {
        this.id = m.getId();
        this.phone = m.getPhone();
        this.passwordHash = m.getPasswordHash();
        this.role = m.getRole().name();
        this.active = m.getStatus() == MemberStatus.ACTIVE;
    }

    public Long getId() { return id; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role)); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return phone; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return active; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return active; }
}
