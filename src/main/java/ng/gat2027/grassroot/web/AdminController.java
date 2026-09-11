package ng.gat2027.grassroot.web;

import ng.gat2027.grassroot.domain.Event;
import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.domain.MemberStatus;
import ng.gat2027.grassroot.domain.ReportStatus;
import ng.gat2027.grassroot.domain.Role;
import ng.gat2027.grassroot.repo.*;
import ng.gat2027.grassroot.security.CurrentUser;
import ng.gat2027.grassroot.service.*;
import ng.gat2027.grassroot.web.forms.AdminFilter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final CurrentUser currentUser; private final AnalyticsService analytics; private final MemberService memberService; private final ReportService reportService;
    private final SettingsService settings; private final CsvImportService importer; private final MemberRepository members; private final EventService events;
    private final ZoneRepository zones; private final StateRepository states; private final LgaRepository lgas; private final WardRepository wards; private final PollingUnitRepository pus;

    public AdminController(CurrentUser currentUser, AnalyticsService analytics, MemberService memberService, ReportService reportService, SettingsService settings, CsvImportService importer,
                           MemberRepository members, EventService events, ZoneRepository zones, StateRepository states, LgaRepository lgas, WardRepository wards, PollingUnitRepository pus) {
        this.currentUser = currentUser; this.analytics = analytics; this.memberService = memberService; this.reportService = reportService; this.settings = settings; this.importer = importer;
        this.members = members; this.events = events; this.zones = zones; this.states = states; this.lgas = lgas; this.wards = wards; this.pus = pus;
    }

    /** Shell (sidebar, filter bar options) for every coordination-centre page. */
    private Member shell(Model model, String title, AdminFilter f) {
        Member u = currentUser.require();
        boolean admin = u.isAdmin();
        boolean grandPatron = u.getRole() == Role.GRAND_PATRON;
        boolean stateLocked = u.getRole() == Role.COORDINATOR;
        if (stateLocked) { f.setZoneId(u.getZoneId()); f.setStateId(u.getStateId()); }
        else if (grandPatron) { f.setZoneId(u.getZoneId()); }
        List<NavItem> nav = new ArrayList<>();
        nav.add(NavItem.exact("/admin", "Overview", "▦", "Analytics"));
        nav.add(NavItem.of("/admin/breakdown", "Breakdown by Area", "▥", "Analytics"));
        nav.add(NavItem.of("/admin/map", "Coverage Map", "◉", "Analytics"));
        nav.add(NavItem.of("/admin/referrals", "Referral Leaderboard", "⇄", "Analytics"));
        nav.add(NavItem.of("/admin/members", "Members", "☷", "Operations").withCount(analytics.countMembers(u, new AdminFilter())));
        nav.add(NavItem.of("/admin/reports", "Field Reports", "▤", "Operations").withCount(analytics.openReportsFor(u)));
        if (admin) {
            nav.add(NavItem.of("/admin/events", "Events", "◈", "Operations"));
            nav.add(NavItem.of("/admin/locations", "Location Data", "⌖", "Operations"));
            nav.add(NavItem.of("/admin/settings", "Settings & Age Limit", "⚙", "Operations"));
        }
        nav.add(NavItem.of("/docs/GAT-2027-User-Manual.pdf", "User Manual (PDF)", "▣", "Help").asExternal());
        nav.add(NavItem.of("/dashboard", "My Member Dashboard", "▣", "Shortcuts"));
        nav.add(NavItem.of("/", "Public Website", "⌂", "Shortcuts"));
        String stateName = u.getStateId() == null ? null : states.findById(u.getStateId()).map(s -> s.getName()).orElse(null);
        String zoneName = u.getZoneId() == null ? null : zones.findById(u.getZoneId()).map(z -> z.getName()).orElse(null);
        model.addAttribute("nav", nav);
        model.addAttribute("shellTitle", title);
        model.addAttribute("shellCrumb", admin ? "Coordination centre · All states"
            : grandPatron ? "Coordination centre · " + (zoneName == null ? "Your zone" : zoneName) + " only"
            : "Coordination centre · " + (stateName == null ? "Your state" : stateName) + " only");
        model.addAttribute("shellSubtitle", "Coordination Centre · 2027");
        model.addAttribute("shellUserLine", admin ? "National administrator" : grandPatron ? "Grand Patron" : "State coordinator");
        model.addAttribute("me", u);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("stateLocked", stateLocked);
        model.addAttribute("filter", f);
        model.addAttribute("zoneOptions", zones.findAllByOrderByNameAsc());
        model.addAttribute("stateOptions", f.getZoneId() == null ? states.findAllByOrderByNameAsc() : states.findByZoneIdOrderByNameAsc(f.getZoneId()));
        model.addAttribute("lgaOptions", f.getStateId() == null ? List.of() : lgas.findByStateIdOrderByNameAsc(f.getStateId()));
        model.addAttribute("wardOptions", f.getLgaId() == null ? List.of() : wards.findByLgaIdOrderByNameAsc(f.getLgaId()));
        model.addAttribute("puOptions", f.getWardId() == null ? List.of() : pus.findByWardIdOrderByCodeAscNameAsc(f.getWardId()));
        return u;
    }

    @GetMapping
    public String overview(@ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "Overview", f);
        var s = analytics.summary(u, f);
        model.addAttribute("s", s);
        model.addAttribute("genderRows", List.of(new AnalyticsService.NameCount("Male", s.male()), new AnalyticsService.NameCount("Female", s.female())));
        // 30-day series
        List<Object[]> days = new ArrayList<>();
        LocalDate today = LocalDate.now();
        long max = 1;
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long n = s.daily().stream().filter(x -> x.day().equals(d)).mapToLong(AnalyticsService.Daily::n).sum();
            max = Math.max(max, n); days.add(new Object[]{d, n});
        }
        model.addAttribute("days", days); model.addAttribute("daysMax", max);
        model.addAttribute("daysTotal", days.stream().mapToLong(d -> (Long) d[1]).sum());
        model.addAttribute("halfState", (s.byState().size() + 1) / 2);
        model.addAttribute("eventStats", events.stats());
        return "admin/overview";
    }

    @GetMapping("/breakdown")
    public String breakdown(@ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "Breakdown by Area", f);
        String level = f.getLevel() == null ? "state" : f.getLevel();
        var rows = analytics.breakdown(u, f, level);
        model.addAttribute("level", level); model.addAttribute("rows", rows);
        model.addAttribute("total", Math.max(1, rows.stream().mapToLong(AnalyticsService.AreaRow::members).sum()));
        return "admin/breakdown";
    }

    @GetMapping("/members")
    public String membersList(@ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "Members", f);
        int page = f.getPage() == null || f.getPage() < 1 ? 1 : f.getPage(), size = 50;
        long total = analytics.countMembers(u, f);
        model.addAttribute("rows", analytics.listMembers(u, f, page, size));
        model.addAttribute("total", total); model.addAttribute("page", page); model.addAttribute("pages", Math.max(1, (total + size - 1) / size));
        return "admin/members";
    }

    @GetMapping("/members/{id}")
    public String memberDetail(@PathVariable Long id, @ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "Member profile", f);
        Member m = members.findById(id).orElse(null);
        if (m == null) return "redirect:/admin/members";
        if (u.getRole() == Role.COORDINATOR && !u.getStateId().equals(m.getStateId())) return "redirect:/admin/members";
        if (u.getRole() == Role.GRAND_PATRON && !u.getZoneId().equals(m.getZoneId())) return "redirect:/admin/members";
        model.addAttribute("p", analytics.profile(m));
        model.addAttribute("refs", analytics.referrals(m.getId()));
        model.addAttribute("self", u.getId().equals(m.getId()));
        return "admin/member";
    }

    @PostMapping("/members/{id}/role")
    public String setRole(@PathVariable Long id, @RequestParam String role, RedirectAttributes ra) {
        try { memberService.setRole(currentUser.require(), id, Role.valueOf(role)); ra.addFlashAttribute("success", "Role updated."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/members/" + id;
    }

    @PostMapping("/members/{id}/status")
    public String setStatus(@PathVariable Long id, @RequestParam String status, RedirectAttributes ra) {
        try { memberService.setStatus(currentUser.require(), id, MemberStatus.valueOf(status)); ra.addFlashAttribute("success", "Status updated."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/members/" + id;
    }

    @PostMapping("/members/{id}/password")
    public String resetPassword(@PathVariable Long id, @RequestParam String password, RedirectAttributes ra) {
        try { memberService.resetPassword(id, password); ra.addFlashAttribute("success", "Password reset. Tell the member their new password."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/members/" + id;
    }

    @GetMapping("/referrals")
    public String leaderboard(@ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "Referral Leaderboard", f);
        var rows = analytics.summary(u, f).topReferrers();
        model.addAttribute("rows", rows);
        model.addAttribute("max", Math.max(1, rows.stream().mapToLong(AnalyticsService.Referrer::n).max().orElse(1)));
        return "admin/referrals";
    }

    @GetMapping("/map")
    public String map(@ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "Coverage Map", f);
        model.addAttribute("points", analytics.mapPoints(u, f));
        return "admin/map";
    }

    @GetMapping("/reports")
    public String reportsList(@ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "Field Reports", f);
        String status = f.getStatus() == null ? "OPEN" : f.getStatus();
        model.addAttribute("status", status);
        model.addAttribute("rows", analytics.reports(u, f, status.isEmpty() ? null : status));
        return "admin/reports";
    }

    @PostMapping("/reports/{id}")
    public String triage(@PathVariable Long id, @RequestParam String status, @RequestParam(required = false) String adminNote, @RequestParam(required = false) String back, RedirectAttributes ra) {
        currentUser.require();
        try { reportService.triage(id, ReportStatus.valueOf(status), adminNote); ra.addFlashAttribute("success", "Report updated."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/reports" + (back != null && back.startsWith("?") ? back : "");
    }

    @GetMapping("/locations")
    public String locations(@ModelAttribute AdminFilter f, Model model) {
        shell(model, "Location Data", f);
        model.addAttribute("ls", analytics.locationStats());
        return "admin/locations";
    }

    @PostMapping("/locations/import")
    public String importCsv(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        try {
            if (file.isEmpty()) throw new IllegalArgumentException("Choose a CSV file first");
            var s = importer.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
            ra.addFlashAttribute("success", s.text());
        } catch (Exception e) { ra.addFlashAttribute("error", "Import failed: " + e.getMessage()); }
        return "redirect:/admin/locations";
    }

    @GetMapping("/events")
    public String eventsList(@ModelAttribute AdminFilter f, Model model) {
        shell(model, "Events", f);
        model.addAttribute("rows", events.all());
        return "admin/events";
    }

    @GetMapping("/events/new")
    public String newEventForm(@ModelAttribute AdminFilter f, Model model) {
        shell(model, "New event", f);
        model.addAttribute("e", new Event());
        return "admin/event-form";
    }

    @GetMapping("/events/{id}/edit")
    public String editEventForm(@PathVariable Long id, @ModelAttribute AdminFilter f, Model model) {
        shell(model, "Edit event", f);
        Event e = events.find(id).orElse(null);
        if (e == null) return "redirect:/admin/events";
        model.addAttribute("e", e);
        return "admin/event-form";
    }

    @PostMapping("/events")
    public String createEvent(@RequestParam String title, @RequestParam LocalDate eventDate, @RequestParam(required = false) String location,
                               @RequestParam String description, @RequestParam(required = false) String coverImageUrl, @RequestParam(required = false) String videoUrl,
                               @RequestParam(required = false) String photoGalleryUrl, @RequestParam(required = false) String published, RedirectAttributes ra) {
        try { events.save(null, title, eventDate, location, description, coverImageUrl, videoUrl, photoGalleryUrl, published != null); ra.addFlashAttribute("success", "Event created."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/events";
    }

    @PostMapping("/events/{id}")
    public String updateEvent(@PathVariable Long id, @RequestParam String title, @RequestParam LocalDate eventDate, @RequestParam(required = false) String location,
                               @RequestParam String description, @RequestParam(required = false) String coverImageUrl, @RequestParam(required = false) String videoUrl,
                               @RequestParam(required = false) String photoGalleryUrl, @RequestParam(required = false) String published, RedirectAttributes ra) {
        try { events.save(id, title, eventDate, location, description, coverImageUrl, videoUrl, photoGalleryUrl, published != null); ra.addFlashAttribute("success", "Event updated."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/events";
    }

    @PostMapping("/events/{id}/delete")
    public String deleteEvent(@PathVariable Long id, RedirectAttributes ra) {
        try { events.delete(id); ra.addFlashAttribute("success", "Event deleted."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/events";
    }

    @GetMapping("/settings")
    public String settingsPage(@ModelAttribute AdminFilter f, Model model) {
        shell(model, "Settings & Age Limit", f);
        model.addAttribute("rules", settings.ageRules());
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam int minAge, @RequestParam(required = false) Integer maxAge, @RequestParam(required = false) String requireDob, RedirectAttributes ra) {
        if (minAge < 1 || minAge > 120) ra.addFlashAttribute("error", "Minimum age must be between 1 and 120");
        else if (maxAge != null && (maxAge < minAge || maxAge > 130)) ra.addFlashAttribute("error", "Maximum age must be greater than the minimum age (or left blank)");
        else { settings.save(new SettingsService.AgeRules(minAge, maxAge, requireDob != null)); ra.addFlashAttribute("success", "Settings saved. New registrations now use these limits."); }
        return "redirect:/admin/settings";
    }
}
