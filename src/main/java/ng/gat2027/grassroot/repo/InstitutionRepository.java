package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.Institution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstitutionRepository extends JpaRepository<Institution, Long> {
    List<Institution> findAllByOrderByTypeAscOwnershipAscNameAsc();
    List<Institution> findByTypeAndOwnershipOrderByNameAsc(String type, String ownership);
}
