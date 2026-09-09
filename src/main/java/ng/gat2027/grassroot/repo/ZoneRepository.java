package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findAllByOrderByNameAsc();
    Optional<Zone> findByCode(String code);
}
