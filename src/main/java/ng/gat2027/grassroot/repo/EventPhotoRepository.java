package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.EventPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventPhotoRepository extends JpaRepository<EventPhoto, Long> {
    List<EventPhoto> findByEventIdOrderBySortOrderAsc(Long eventId);
    long countByEventId(Long eventId);
}
