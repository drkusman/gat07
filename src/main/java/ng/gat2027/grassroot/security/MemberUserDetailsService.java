package ng.gat2027.grassroot.security;

import ng.gat2027.grassroot.repo.MemberRepository;
import ng.gat2027.grassroot.service.Codes;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MemberUserDetailsService implements UserDetailsService {
    private final MemberRepository members;

    public MemberUserDetailsService(MemberRepository members) { this.members = members; }

    @Override
    public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
        return members.findByPhone(Codes.normalizePhone(phone))
            .map(MemberPrincipal::new)
            .orElseThrow(() -> new UsernameNotFoundException("No member with phone " + phone));
    }
}
