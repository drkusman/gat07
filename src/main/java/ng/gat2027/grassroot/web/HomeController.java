package ng.gat2027.grassroot.web;

import ng.gat2027.grassroot.domain.Event;
import ng.gat2027.grassroot.domain.EventPhoto;
import ng.gat2027.grassroot.service.AnalyticsService;
import ng.gat2027.grassroot.service.EventService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Base64;
import java.util.List;

@Controller
public class HomeController {
    public record GalleryTab(Event event, List<EventPhoto> photos) {}

    private final AnalyticsService analytics;
    private final EventService events;

    public HomeController(AnalyticsService analytics, EventService events) { this.analytics = analytics; this.events = events; }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("stats", analytics.locationStats());
        return "index";
    }

    @GetMapping("/events")
    public String eventsPage(Model model) {
        model.addAttribute("allEvents", events.published());
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
