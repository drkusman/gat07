package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.State;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StateRepository extends JpaRepository<State, Long> {
    List<State> findAllByOrderByNameAsc();
    List<State> findByZoneIdOrderByNameAsc(Long zoneId);
}
