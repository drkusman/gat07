package ng.gat2027.grassroot.web;

import ng.gat2027.grassroot.service.AnalyticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final AnalyticsService analytics;

    public HomeController(AnalyticsService analytics) { this.analytics = analytics; }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("stats", analytics.locationStats());
        return "index";
    }
}
