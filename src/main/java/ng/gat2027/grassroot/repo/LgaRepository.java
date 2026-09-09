package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.Lga;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LgaRepository extends JpaRepository<Lga, Long> {
    List<Lga> findByStateIdOrderByNameAsc(Long stateId);
    Optional<Lga> findByIdAndStateId(Long id, Long stateId);
}
