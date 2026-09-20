package ng.gat2027.grassroot.web;

import ng.gat2027.grassroot.domain.Event;
import ng.gat2027.grassroot.domain.EventPhoto;
import ng.gat2027.grassroot.domain.PromoVideo;
import ng.gat2027.grassroot.service.AnalyticsService;
import ng.gat2027.grassroot.service.AnnouncementService;
import ng.gat2027.grassroot.service.EventService;
import ng.gat2027.grassroot.service.PromoVideoService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomeController {
    public record GalleryTab(Event event, List<EventPhoto> photos) {}

    private final AnalyticsService analytics;
    private final EventService events;
    private final AnnouncementService announcements;
    private final PromoVideoService promoVideos;

    public HomeController(AnalyticsService analytics, EventService events, AnnouncementService announcements, PromoVideoService promoVideos) {
        this.analytics = analytics; this.events = events; this.announcements = announcements; this.promoVideos = promoVideos;
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) String play, Model model) {
        model.addAttribute("stats", analytics.locationStats());
        model.addAttribute("totalMembers", analytics.totalMembersDisplay());
        List<PromoVideo> active = promoVideos.activeVideos();
        List<PromoVideo> playlist = playlistFor(play, active);
        String currentId = playlist.isEmpty() ? null : playlist.get(0).getYoutubeId();
        model.addAttribute("firstVideoId", currentId);
        model.addAttribute("currentVideoTitle", playlist.isEmpty() ? null : playlist.get(0).getTitle());
        model.addAttribute("videoPlaylist", playlist.stream().map(PromoVideo::getYoutubeId).collect(Collectors.joining(",")));
        // "Up Next" always reflects the normal rotation, independent of any high-priority override on what's playing now.
        model.addAttribute("upNextVideos", active.stream().filter(v -> !v.getYoutubeId().equals(currentId)).limit(10).toList());
        return "index";
    }

    /** A visitor clicking an "Up Next" video plays that one first (for their view only); otherwise the normal, priority-aware playlist. */
    private List<PromoVideo> playlistFor(String play, List<PromoVideo> active) {
        if (play == null) return promoVideos.playlist();
        int idx = -1;
        for (int i = 0; i < active.size(); i++) if (active.get(i).getYoutubeId().equals(play)) { idx = i; break; }
        if (idx < 0) return promoVideos.playlist();
        List<PromoVideo> reordered = new ArrayList<>(active);
        reordered.add(0, reordered.remove(idx));
        return reordered;
    }

    @GetMapping("/news")
    public String newsPage(Model model) {
        model.addAttribute("publicInfo", announcements.publicInfo());
        return "news";
    }

    @GetMapping("/events")
    public String eventsPage(@RequestParam(required = false) String when, Model model) {
        List<Event> published = events.published();
        boolean showUpcoming = !"past".equals(when);
        boolean showPast = !"upcoming".equals(when);
        List<Event> upcoming = showUpcoming ? published.stream().filter(Event::isUpcoming).toList() : List.of();
        List<Event> past = showPast ? published.stream().filter(e -> !e.isUpcoming()).toList() : List.of();
        model.addAttribute("upcomingEvents", upcoming);
        model.addAttribute("pastEvents", past);
        model.addAttribute("emptyMessage", "upcoming".equals(when) ? "No upcoming events right now — check back soon."
            : "past".equals(when) ? "No past events to show yet."
            : "No events published yet — check back soon.");
        model.addAttribute("galleryTabs", events.withPhotos().stream().map(e -> new GalleryTab(e, events.photosFor(e.getId()))).toList());
        return "events";
    }

    @GetMapping("/events/{id}/video")
    public ResponseEntity<byte[]> eventVideo(@PathVariable Long id) {
        Event e = events.find(id).orElse(null);
        if (e == null || !e.isPublished() || !e.hasUploadedVideo()) return ResponseEntity.notFound().build();
        String dataUrl = e.getVideoData();
        int comma = dataUrl.indexOf(',');
        String meta = dataUrl.substring(5, dataUrl.indexOf(';'));
        byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(meta)).body(bytes);
    }
}
