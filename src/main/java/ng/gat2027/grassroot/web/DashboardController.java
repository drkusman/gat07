package ng.gat2027.grassroot.web;

import jakarta.validation.Valid;
import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.repo.FieldReportRepository;
import ng.gat2027.grassroot.repo.MemberRepository;
import ng.gat2027.grassroot.security.CurrentUser;
import ng.gat2027.grassroot.service.AnalyticsService;
import ng.gat2027.grassroot.service.LocationService;
import ng.gat2027.grassroot.service.MemberService;
import ng.gat2027.grassroot.service.SettingsService;
import ng.gat2027.grassroot.web.forms.ProfileForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {
    private final CurrentUser currentUser; private final AnalyticsService analytics; private final MemberService memberService;
    private final MemberRepository members; private final FieldReportRepository reports; private final SettingsService settings;

    public DashboardController(CurrentUser currentUser, AnalyticsService analytics, MemberService memberService, MemberRepository members, FieldReportRepository reports, SettingsService settings) {
        this.currentUser = currentUser; this.analytics = analytics; this.memberService = memberService; this.members = members; this.reports = reports; this.settings = settings;
    }

    /** Sidebar + common shell attributes for every member page. */
    private Member shell(Model model, String title) {
        Member m = currentUser.require();
        List<NavItem> nav = new ArrayList<>();
        nav.add(NavItem.exact("/dashboard", "Overview", "▦", "Dashboard"));
        nav.add(NavItem.of("/dashboard/referrals", "My Referrals", "⇄", "Dashboard").withCount(members.countByReferredBy(m.getId())));
        nav.add(NavItem.of("/dashboard/polling-unit", "My Polling Unit", "◉", "Dashboard"));
        nav.add(NavItem.of("/dashboard/reports", "My Reports", "▤", "Dashboard").withCount(reports.countByMemberId(m.getId())));
        nav.add(NavItem.of("/dashboard/profile", "Profile & Settings", "✎", "Account"));
        nav.add(NavItem.of("/report", "File a Field Report", "✚", "Account"));
        if (m.isStaff()) nav.add(NavItem.of("/admin", "Coordination Dashboard", "★", "Account"));
        nav.add(NavItem.of("/docs/GAT-2027-User-Manual.pdf", "User Manual (PDF)", "▣", "Help").asExternal());
        nav.add(NavItem.of("/", "Public Website", "⌂", "Help"));
        model.addAttribute("nav", nav);
        model.addAttribute("shellTitle", title);
        model.addAttribute("shellCrumb", "Member portal");
        model.addAttribute("shellSubtitle", "Member Portal · 2027");
        model.addAttribute("shellUserLine", m.getMemberCode() + " · " + m.getRole().name().toLowerCase());
        model.addAttribute("me", m);
        return m;
    }

    @GetMapping
    public String overview(Model model) {
        Member m = shell(model, "Overview");
        model.addAttribute("profile", analytics.profile(m));
        model.addAttribute("refs", analytics.referrals(m.getId()));
        model.addAttribute("myReports", reports.findByMemberIdOrderByCreatedAtDesc(m.getId()));
        model.addAttribute("coverage", analytics.coverage(m));
        return "dashboard/overview";
    }

    @GetMapping("/referrals")
    public String referrals(Model model) {
        Member m = shell(model, "My Referrals");
        model.addAttribute("refs", analytics.referrals(m.getId()));
        return "dashboard/referrals";
    }

    @GetMapping("/polling-unit")
    public String pollingUnit(Model model) {
        Member m = shell(model, "My Polling Unit");
        model.addAttribute("profile", analytics.profile(m));
        model.addAttribute("coverage", analytics.coverage(m));
        return "dashboard/polling-unit";
    }

    @GetMapping("/reports")
    public String myReports(Model model) {
        Member m = shell(model, "My Reports");
        model.addAttribute("myReports", reports.findByMemberIdOrderByCreatedAtDesc(m.getId()));
        return "dashboard/reports";
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        Member m = shell(model, "Profile & Settings");
        if (!model.containsAttribute("form")) model.addAttribute("form", ProfileForm.from(m));
        model.addAttribute("profile", analytics.profile(m));
        model.addAttribute("ageRules", settings.ageRules());
        return "dashboard/profile";
    }

    @PostMapping("/profile")
    public String saveProfile(@Valid @ModelAttribute("form") ProfileForm form, BindingResult binding, Model model, RedirectAttributes ra) {
        Member m = shell(model, "Profile & Settings");
        model.addAttribute("profile", analytics.profile(m));
        model.addAttribute("ageRules", settings.ageRules());
        if (binding.hasErrors()) { model.addAttribute("error", binding.getAllErrors().get(0).getDefaultMessage()); return "dashboard/profile"; }
        try {
            memberService.updateProfile(m, form);
            ra.addFlashAttribute("success", "Profile updated successfully.");
            return "redirect:/dashboard/profile";
        } catch (MemberService.MemberException | LocationService.LocationException e) {
            model.addAttribute("error", e.getMessage());
            return "dashboard/profile";
        }
    }

    @PostMapping("/profile/password")
    public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword, RedirectAttributes ra) {
        Member m = currentUser.require();
        try { memberService.changePassword(m, currentPassword, newPassword); ra.addFlashAttribute("success", "Password changed."); }
        catch (MemberService.MemberException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/dashboard/profile";
    }
}
