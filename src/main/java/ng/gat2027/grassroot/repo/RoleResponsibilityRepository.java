package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.RoleResponsibility;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleResponsibilityRepository extends JpaRepository<RoleResponsibility, Long> {
    java.util.List<RoleResponsibility> findAllByOrderByIdAsc();
}
