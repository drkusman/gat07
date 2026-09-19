package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.Announcement;
import ng.gat2027.grassroot.domain.AnnouncementAudience;
import ng.gat2027.grassroot.repo.AnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class AnnouncementService {
    private final AnnouncementRepository repo;

    public AnnouncementService(AnnouncementRepository repo) { this.repo = repo; }

    public List<Announcement> all() { return repo.findAllByOrderByCreatedAtDesc(); }
    public Optional<Announcement> find(Long id) { return repo.findById(id); }

    /** General Information shown on the public home page. */
    public List<Announcement> publicInfo() { return repo.findByAudienceAndPublishedTrueOrderByCreatedAtDesc(AnnouncementAudience.PUBLIC); }

    /** State Breaking News for a logged-in member: nationwide items plus anything scoped to their own state. */
    public List<Announcement> memberNews(Long stateId) { return repo.findMemberNews(stateId); }

    @Transactional
    public Announcement save(Long id, String title, String body, AnnouncementAudience audience, Long stateId, boolean published, Long adminId) {
        Announcement a = id == null ? new Announcement() : repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Announcement not found"));
        boolean isNew = a.getId() == null;
        a.setTitle(title);
        a.setBody(body);
        a.setAudience(audience);
        a.setStateId(audience == AnnouncementAudience.MEMBERS ? stateId : null);
        a.setPublished(published);
        a.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (isNew) a.setCreatedBy(adminId);
        return repo.save(a);
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }
}
