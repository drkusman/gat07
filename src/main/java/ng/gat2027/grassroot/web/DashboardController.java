package ng.gat2027.grassroot.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import ng.gat2027.grassroot.domain.Committee;
import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.domain.Position;
import ng.gat2027.grassroot.repo.FieldReportRepository;
import ng.gat2027.grassroot.repo.MemberRepository;
import ng.gat2027.grassroot.security.CurrentUser;
import ng.gat2027.grassroot.service.AnalyticsService;
import ng.gat2027.grassroot.service.AnnouncementService;
import ng.gat2027.grassroot.service.AppointmentLetterService;
import ng.gat2027.grassroot.service.LocationService;
import ng.gat2027.grassroot.service.MemberService;
import ng.gat2027.grassroot.service.OrganizationService;
import ng.gat2027.grassroot.service.SettingsService;
import ng.gat2027.grassroot.web.forms.ProfileForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {
    private final CurrentUser currentUser; private final AnalyticsService analytics; private final MemberService memberService;
    private final MemberRepository members; private final FieldReportRepository reports; private final SettingsService settings;
    private final AnnouncementService announcements; private final OrganizationService organization; private final AppointmentLetterService letters;

    public DashboardController(CurrentUser currentUser, AnalyticsService analytics, MemberService memberService, MemberRepository members, FieldReportRepository reports, SettingsService settings,
                               AnnouncementService announcements, OrganizationService organization, AppointmentLetterService letters) {
        this.currentUser = currentUser; this.analytics = analytics; this.memberService = memberService; this.members = members; this.reports = reports; this.settings = settings;
        this.announcements = announcements; this.organization = organization; this.letters = letters;
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
        if (m.getPositionId() != null) nav.add(NavItem.of("/dashboard/appointment", "Appointment Letter", "📄", "Account"));
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
        model.addAttribute("stateNews", announcements.memberNews(m.getStateId()));
        return "dashboard/overview";
    }

    @GetMapping("/appointment")
    public String appointmentPage(Model model) {
        Member m = shell(model, "Appointment Letter");
        if (m.getPositionId() == null) return "redirect:/dashboard";
        Position position = organization.findPosition(m.getPositionId()).orElse(null);
        model.addAttribute("positionLabel", position == null ? null
            : position.getTitle() + organization.findCommittee(position.getCommitteeId()).map(c -> " (" + c.getCode() + ")").orElse(""));
        return "dashboard/appointment";
    }

    @GetMapping("/appointment-letter")
    public void appointmentLetter(HttpServletResponse response) throws IOException {
        Member m = currentUser.require();
        try {
            if (m.getPositionId() == null) throw new MemberService.MemberException("No position assigned yet");
            Position position = organization.findPosition(m.getPositionId()).orElseThrow(() -> new MemberService.MemberException("Position not found"));
            Committee committee = organization.findCommittee(position.getCommitteeId()).orElseThrow(() -> new MemberService.MemberException("Committee not found"));
            byte[] pdf = letters.generate(m, position, committee, analytics.profile(m).stateName());
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"Appointment-Letter-" + m.getMemberCode() + ".pdf\"");
            response.setContentLength(pdf.length);
            response.getOutputStream().write(pdf);
            response.getOutputStream().flush();
        } catch (MemberService.MemberException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/appointment/acceptance")
    public String uploadAcceptance(@RequestParam String acceptanceData, RedirectAttributes ra) {
        try { memberService.uploadAcceptanceLetter(currentUser.require(), acceptanceData); ra.addFlashAttribute("success", "Thanks - your signed acceptance letter has been submitted."); }
        catch (MemberService.MemberException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/dashboard/appointment";
    }

    @GetMapping("/appointment/acceptance")
    public void viewOwnAcceptance(HttpServletResponse response) throws IOException {
        Member m = currentUser.require();
        if (!m.hasAcceptance()) { response.sendError(HttpServletResponse.SC_NOT_FOUND, "Nothing submitted yet"); return; }
        writeDataUrl(response, m.getAcceptanceData());
    }

    private void writeDataUrl(HttpServletResponse response, String dataUrl) throws IOException {
        int comma = dataUrl.indexOf(',');
        String meta = dataUrl.substring(5, comma); // "image/jpeg;base64"
        String contentType = meta.split(";")[0];
        byte[] bytes = java.util.Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        response.setContentType(contentType);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
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
