package ng.gat2027.grassroot.security;

import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.repo.MemberRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Resolves the logged-in member (fresh from the database) from the security context. */
@Component
public class CurrentUser {
    private final MemberRepository members;

    public CurrentUser(MemberRepository members) { this.members = members; }

    public Optional<Member> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof MemberPrincipal p)) return Optional.empty();
        return members.findById(p.getId()).filter(Member::isActive);
    }

    public Member require() {
        return get().orElseThrow(() -> new IllegalStateException("Not logged in"));
    }
}
