package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.Announcement;
import ng.gat2027.grassroot.domain.AnnouncementAudience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findAllByOrderByCreatedAtDesc();
    List<Announcement> findByAudienceAndPublishedTrueOrderByCreatedAtDesc(AnnouncementAudience audience);

    /** Every published nationwide MEMBERS item, plus any scoped to the given state. */
    @Query("select a from Announcement a where a.audience = ng.gat2027.grassroot.domain.AnnouncementAudience.MEMBERS "
        + "and a.published = true and (a.stateId is null or a.stateId = :stateId) order by a.createdAt desc")
    List<Announcement> findMemberNews(@Param("stateId") Long stateId);
}
