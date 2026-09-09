package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WardRepository extends JpaRepository<Ward, Long> {
    List<Ward> findByLgaIdOrderByNameAsc(Long lgaId);
    Optional<Ward> findByIdAndLgaId(Long id, Long lgaId);
    Optional<Ward> findFirstByLgaIdAndNameIgnoreCase(Long lgaId, String name);
}
