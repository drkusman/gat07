package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.PromoVideo;
import ng.gat2027.grassroot.repo.PromoVideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PromoVideoService {
    private static final Pattern ID_IN_URL = Pattern.compile("(?:v=|youtu\\.be/|embed/|shorts/)([A-Za-z0-9_-]{11})");
    private static final Pattern BARE_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final long DEFAULT_TTL_HOURS = 24;

    private final PromoVideoRepository repo;

    public PromoVideoService(PromoVideoRepository repo) { this.repo = repo; }

    public List<PromoVideo> list() { return repo.findAllByOrderByIdAsc(); }
    public Optional<PromoVideo> find(Long id) { return repo.findById(id); }

    /** Unexpired videos in the order they were added. */
    public List<PromoVideo> activeVideos() {
        return repo.findAllByOrderByIdAsc().stream().filter(v -> !v.isExpired()).toList();
    }

    /** What actually plays on the home page right now: any video(s) still flagged high priority play
     *  exclusively; otherwise every active video loops in the normal order. */
    public List<PromoVideo> playlist() {
        List<PromoVideo> active = activeVideos();
        List<PromoVideo> priority = active.stream().filter(PromoVideo::isPriorityActive).toList();
        return priority.isEmpty() ? active : priority;
    }

    @Transactional
    public PromoVideo add(String urlOrId, String title, LocalDateTime expiresAt, Integer priorityHours, Long adminId) {
        PromoVideo v = new PromoVideo();
        v.setYoutubeId(extractId(urlOrId));
        v.setTitle(blankToNull(title));
        v.setCreatedBy(adminId);
        v.setExpiresAt(expiresAt != null ? expiresAt : defaultExpiry());
        v.setPriorityUntil(priorityUntilFrom(priorityHours));
        return repo.save(v);
    }

    @Transactional
    public PromoVideo update(Long id, String urlOrId, String title, LocalDateTime expiresAt, Integer priorityHours) {
        PromoVideo v = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Video not found"));
        v.setYoutubeId(extractId(urlOrId));
        v.setTitle(blankToNull(title));
        v.setExpiresAt(expiresAt != null ? expiresAt : defaultExpiry());
        v.setPriorityUntil(priorityUntilFrom(priorityHours));
        return repo.save(v);
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }

    private static LocalDateTime priorityUntilFrom(Integer hours) {
        return hours != null && hours > 0 ? LocalDateTime.now(ZoneOffset.UTC).plusHours(hours) : null;
    }

    private static LocalDateTime defaultExpiry() { return LocalDateTime.now(ZoneOffset.UTC).plusHours(DEFAULT_TTL_HOURS); }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static String extractId(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Paste a YouTube video link or ID");
        String trimmed = input.trim();
        Matcher m = ID_IN_URL.matcher(trimmed);
        if (m.find()) return m.group(1);
        if (BARE_ID.matcher(trimmed).matches()) return trimmed;
        throw new IllegalArgumentException("Couldn't find a YouTube video ID in that link");
    }
}
