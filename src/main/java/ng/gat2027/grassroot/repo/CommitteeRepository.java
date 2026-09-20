package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.Committee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommitteeRepository extends JpaRepository<Committee, Long> {
    List<Committee> findAllByOrderByNameAsc();
    Optional<Committee> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
}
