package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findAllByOrderByTitleAsc();
    long countByCommitteeId(Long committeeId);
}
