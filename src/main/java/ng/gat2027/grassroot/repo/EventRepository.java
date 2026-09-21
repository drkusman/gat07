package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.Event;
import ng.gat2027.grassroot.domain.PostType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findAllByOrderByEventDateDesc();
    List<Event> findByZoneIdOrderByEventDateDesc(Long zoneId);
    List<Event> findByStateIdOrderByEventDateDesc(Long stateId);
    List<Event> findByCreatedByOrderByEventDateDesc(Long createdBy);
    List<Event> findByPublishedTrueAndApprovedTrueAndPostTypeOrderByEventDateDesc(PostType postType);
    Optional<Event> findBySlug(String slug);
    boolean existsBySlug(String slug);
    long countByPostType(PostType postType);
    long countByPublishedTrueAndApprovedTrueAndPostType(PostType postType);
    long countByPublishedTrueAndApprovedTrueAndPostTypeAndEventDateGreaterThanEqual(PostType postType, LocalDate date);
    long countByApprovedFalse();
}
