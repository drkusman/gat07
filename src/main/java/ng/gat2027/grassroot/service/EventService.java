package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.Event;
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

    public EventService(EventRepository repo) { this.repo = repo; }

    public List<Event> all() { return repo.findAllByOrderByEventDateDesc(); }
    public List<Event> published() { return repo.findByPublishedTrueOrderByEventDateDesc(); }
    public Optional<Event> find(Long id) { return repo.findById(id); }

    public Stats stats() {
        long total = repo.count();
        long published = repo.countByPublishedTrue();
        long upcoming = repo.countByPublishedTrueAndEventDateGreaterThanEqual(LocalDate.now());
        Event next = repo.findByPublishedTrueOrderByEventDateDesc().stream()
            .filter(Event::isUpcoming)
            .min((a, b) -> a.getEventDate().compareTo(b.getEventDate()))
            .orElse(null);
        return new Stats(total, published, upcoming, next);
    }

    @Transactional
    public Event save(Long id, String title, LocalDate eventDate, String location, String description,
                       String coverImageUrl, String videoUrl, String photoGalleryUrl, boolean published) {
        Event e = id == null ? new Event() : repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        boolean isNew = e.getId() == null;
        e.setTitle(title);
        e.setEventDate(eventDate);
        e.setLocation(blankToNull(location));
        e.setDescription(description);
        e.setCoverImageUrl(blankToNull(coverImageUrl));
        e.setVideoUrl(blankToNull(videoUrl));
        e.setPhotoGalleryUrl(blankToNull(photoGalleryUrl));
        e.setPublished(published);
        e.setUpdatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        if (isNew || e.getSlug() == null || e.getSlug().isBlank()) e.setSlug(uniqueSlug(title, id));
        return repo.save(e);
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
