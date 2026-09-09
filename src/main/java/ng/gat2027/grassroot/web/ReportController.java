package ng.gat2027.grassroot.web;

import jakarta.validation.Valid;
import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.domain.ReportCategory;
import ng.gat2027.grassroot.security.CurrentUser;
import ng.gat2027.grassroot.service.AnalyticsService;
import ng.gat2027.grassroot.service.ReportService;
import ng.gat2027.grassroot.web.forms.ReportForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReportController {
    private final CurrentUser currentUser; private final ReportService reportService; private final AnalyticsService analytics;

    public ReportController(CurrentUser currentUser, ReportService reportService, AnalyticsService analytics) {
        this.currentUser = currentUser; this.reportService = reportService; this.analytics = analytics;
    }

    private void prepare(Model model, Member m) {
        String from = analytics.profile(m).locationLine();
        model.addAttribute("from", from.isBlank() ? "No polling unit on profile" : from);
        model.addAttribute("categories", ReportCategory.values());
    }

    @GetMapping("/report")
    public String form(Model model) {
        Member m = currentUser.require();
        prepare(model, m);
        if (!model.containsAttribute("form")) model.addAttribute("form", new ReportForm());
        return "report";
    }

    @PostMapping("/report")
    public String submit(@Valid @ModelAttribute("form") ReportForm form, BindingResult binding, Model model, RedirectAttributes ra) {
        Member m = currentUser.require();
        if (binding.hasErrors()) { prepare(model, m); model.addAttribute("error", binding.getAllErrors().get(0).getDefaultMessage()); return "report"; }
        reportService.file(m, form);
        ra.addFlashAttribute("success", "Report submitted. Thank you!");
        return "redirect:/dashboard/reports";
    }
}
