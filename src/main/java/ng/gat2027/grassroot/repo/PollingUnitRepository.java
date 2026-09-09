package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.PollingUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PollingUnitRepository extends JpaRepository<PollingUnit, Long> {
    List<PollingUnit> findByWardIdOrderByCodeAscNameAsc(Long wardId);
    Optional<PollingUnit> findByIdAndWardId(Long id, Long wardId);
    Optional<PollingUnit> findFirstByWardIdAndNameIgnoreCase(Long wardId, String name);
}
