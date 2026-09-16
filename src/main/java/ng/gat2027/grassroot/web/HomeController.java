package ng.gat2027.grassroot.web;

import ng.gat2027.grassroot.domain.Event;
import ng.gat2027.grassroot.domain.EventPhoto;
import ng.gat2027.grassroot.service.AnalyticsService;
import ng.gat2027.grassroot.service.EventService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
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
        List<Event> published = events.published();
        model.addAttribute("upcomingEvents", published.stream().filter(Event::isUpcoming).sorted(Comparator.comparing(Event::getEventDate)).toList());
        model.addAttribute("pastEvents", published.stream().filter(e -> !e.isUpcoming()).toList());
        model.addAttribute("galleryTabs", events.withPhotos().stream().map(e -> new GalleryTab(e, events.photosFor(e.getId()))).toList());
        return "events";
    }
}
