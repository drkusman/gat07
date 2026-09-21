package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.Event;
import ng.gat2027.grassroot.domain.EventPhoto;
import ng.gat2027.grassroot.domain.PostType;
import ng.gat2027.grassroot.repo.EventPhotoRepository;
import ng.gat2027.grassroot.repo.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class EventService {
    public record Stats(long total, long published, long upcoming, Event nextEvent) {}

    private final EventRepository repo;
    private final EventPhotoRepository photoRepo;

    public EventService(EventRepository repo, EventPhotoRepository photoRepo) { this.repo = repo; this.photoRepo = photoRepo; }

    public List<Event> all() { return repo.findAllByOrderByEventDateDesc(); }
    public List<Event> forZone(Long zoneId) { return repo.findByZoneIdOrderByEventDateDesc(zoneId); }
    public List<Event> forState(Long stateId) { return repo.findByStateIdOrderByEventDateDesc(stateId); }
    public List<Event> forCreator(Long createdBy) { return repo.findByCreatedByOrderByEventDateDesc(createdBy); }
    public List<Event> published(PostType postType) { return repo.findByPublishedTrueAndApprovedTrueAndPostTypeOrderByEventDateDesc(postType); }
    public long countPendingApproval() { return repo.countByApprovedFalse(); }
    public Optional<Event> find(Long id) { return repo.findById(id); }
    public List<EventPhoto> photosFor(Long eventId) { return photoRepo.findByEventIdOrderBySortOrderAsc(eventId); }
    public long photoCount(Long eventId) { return photoRepo.countByEventId(eventId); }

    /** Posts (published, any date) of the given type that have at least one uploaded photo, for the public photo gallery tabs. */
    public List<Event> withPhotos(PostType postType) {
        return published(postType).stream().filter(e -> photoCount(e.getId()) > 0).toList();
    }

    @Transactional
    public void addPhotos(Long eventId, List<String> dataUrls) {
        if (dataUrls == null || dataUrls.isEmpty()) return;
        int start = (int) photoCount(eventId);
        for (int i = 0; i < dataUrls.size(); i++) {
            String data = dataUrls.get(i);
            if (data == null || data.isBlank()) continue;
            EventPhoto p = new EventPhoto();
            p.setEventId(eventId); p.setImageData(data); p.setSortOrder(start + i);
            photoRepo.save(p);
        }
    }

    @Transactional
    public void deletePhoto(Long photoId) { photoRepo.deleteById(photoId); }

    @Transactional
    public void setVideo(Long eventId, String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) return;
        Event e = repo.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        e.setVideoData(dataUrl);
        repo.save(e);
    }

    @Transactional
    public void clearVideo(Long eventId) {
        Event e = repo.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        e.setVideoData(null);
        repo.save(e);
    }

    public Stats stats() {
        long total = repo.countByPostType(PostType.EVENT);
        long published = repo.countByPublishedTrueAndApprovedTrueAndPostType(PostType.EVENT);
        long upcoming = repo.countByPublishedTrueAndApprovedTrueAndPostTypeAndEventDateGreaterThanEqual(PostType.EVENT, LocalDate.now());
        Event next = repo.findByPublishedTrueAndApprovedTrueAndPostTypeOrderByEventDateDesc(PostType.EVENT).stream()
            .filter(Event::isUpcoming)
            .min((a, b) -> a.getEventDate().compareTo(b.getEventDate()))
            .orElse(null);
        return new Stats(total, published, upcoming, next);
    }

    /** Zone/state scope and creator are only set when the event is created - editing never changes them.
     *  approved is set every save from the caller's role: Admin/Coordinator/Zonal edits always approve;
     *  a Media Coordinator's own save (create or edit) always resets it to pending. */
    @Transactional
    public Event save(Long id, String title, LocalDate eventDate, String location, String description, boolean published,
                       Long creatorZoneId, Long creatorStateId, Long creatorId, boolean approved, PostType postType) {
        Event e = id == null ? new Event() : repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        boolean isNew = e.getId() == null;
        e.setTitle(title);
        e.setEventDate(eventDate);
        e.setLocation(blankToNull(location));
        e.setDescription(description);
        e.setPublished(published);
        e.setApproved(approved);
        e.setPostType(postType);
        e.setUpdatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        if (isNew) { e.setZoneId(creatorZoneId); e.setStateId(creatorStateId); e.setCreatedBy(creatorId); }
        if (isNew || e.getSlug() == null || e.getSlug().isBlank()) e.setSlug(uniqueSlug(title, id));
        return repo.save(e);
    }

    @Transactional
    public void approve(Long id) {
        Event e = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        e.setApproved(true);
        repo.save(e);
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }

    private String uniqueSlug(String title, Long excludingId) {
        String base = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isBlank()) base = "event";
        String slug = base;
        int i = 2;
        while (repo.findBySlug(slug).filter(e -> !e.getId().equals(excludingId)).isPresent()) slug = base + "-" + i++;
        return slug;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
