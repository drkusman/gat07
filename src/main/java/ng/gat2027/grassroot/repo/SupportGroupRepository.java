package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.SupportGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportGroupRepository extends JpaRepository<SupportGroup, Long> {
    List<SupportGroup> findAllByOrderByCreatedAtDesc();
}
