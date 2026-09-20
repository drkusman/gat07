package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.Role;
import ng.gat2027.grassroot.domain.RoleResponsibility;
import ng.gat2027.grassroot.repo.RoleResponsibilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class RoleResponsibilityService {
    public record RoleSection(Role role, List<RoleResponsibility> items) {}

    private final RoleResponsibilityRepository repo;

    public RoleResponsibilityService(RoleResponsibilityRepository repo) { this.repo = repo; }

    /** One section per Role, in declaration order, each with its own responsibilities in the order they were added. */
    public List<RoleSection> byRole() {
        Map<Role, List<RoleResponsibility>> grouped = new EnumMap<>(Role.class);
        for (Role r : Role.values()) grouped.put(r, new ArrayList<>());
        for (RoleResponsibility r : repo.findAllByOrderByIdAsc()) grouped.get(r.getRole()).add(r);
        List<RoleSection> out = new ArrayList<>();
        for (Role r : Role.values()) out.add(new RoleSection(r, grouped.get(r)));
        return out;
    }

    @Transactional
    public RoleResponsibility add(Role role, String description) {
        RoleResponsibility r = new RoleResponsibility();
        r.setRole(role);
        r.setDescription(description.trim());
        return repo.save(r);
    }

    @Transactional
    public void update(Long id, String description) {
        RoleResponsibility r = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Responsibility not found"));
        r.setDescription(description.trim());
        repo.save(r);
    }

    @Transactional
    public void revoke(Long id) { repo.deleteById(id); }
}
