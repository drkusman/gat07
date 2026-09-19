package ng.gat2027.grassroot.web;

import ng.gat2027.grassroot.domain.Event;
import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.domain.MemberStatus;
import ng.gat2027.grassroot.domain.PromotionStage;
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

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/admin")
public class AdminController {
    public record EventRow(Event event, String scopeLabel) {}
    public record PromotionRow(Member member, String requestedByName) {}

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
        boolean zonalCoordinator = u.getRole() == Role.ZONAL_COORDINATOR;
        boolean stateCoordinator = u.getRole() == Role.COORDINATOR;
        boolean lgaCoordinator = u.getRole() == Role.LGA_COORDINATOR;
        boolean wardCoordinator = u.getRole() == Role.WARD_COORDINATOR;
        boolean puCoordinator = u.getRole() == Role.POLLING_UNIT_COORDINATOR;
        boolean stateLocked = stateCoordinator || lgaCoordinator || wardCoordinator || puCoordinator;
        boolean lgaLocked = lgaCoordinator || wardCoordinator || puCoordinator;
        boolean wardLocked = wardCoordinator || puCoordinator;
        boolean puLocked = puCoordinator;
        boolean canManageEvents = admin || zonalCoordinator || stateCoordinator;
        if (stateLocked) { f.setZoneId(u.getZoneId()); f.setStateId(u.getStateId()); }
        else if (grandPatron || zonalCoordinator) { f.setZoneId(u.getZoneId()); }
        if (lgaLocked) f.setLgaId(u.getLgaId());
        if (wardLocked) f.setWardId(u.getWardId());
        if (puLocked) f.setPollingUnitId(u.getPollingUnitId());
        List<NavItem> nav = new ArrayList<>();
        nav.add(NavItem.exact("/admin", "Overview", "▦", "Analytics"));
        nav.add(NavItem.of("/admin/breakdown", "Breakdown by Area", "▥", "Analytics"));
        nav.add(NavItem.of("/admin/map", "Coverage Map", "◉", "Analytics"));
        nav.add(NavItem.of("/admin/referrals", "Referral Leaderboard", "⇄", "Analytics"));
        nav.add(NavItem.of("/admin/members", "Members", "☷", "Operations").withCount(analytics.countMembers(u, new AdminFilter())));
        nav.add(NavItem.of("/admin/reports", "Field Reports", "▤", "Operations").withCount(analytics.openReportsFor(u)));
        if (canManageEvents) nav.add(NavItem.of("/admin/events", "Events", "◈", "Operations"));
        if (admin || zonalCoordinator) {
            long pendingCount = admin ? members.countByPendingRoleIsNotNull()
                : members.countByPendingRoleIsNotNullAndZoneIdAndPendingRoleStage(u.getZoneId(), PromotionStage.ZONAL);
            nav.add(NavItem.of("/admin/promotions", "Pending Promotions", "★", "Operations").withCount(pendingCount));
        }
        if (admin) {
            nav.add(NavItem.of("/admin/locations", "Location Data", "⌖", "Operations"));
            nav.add(NavItem.of("/admin/settings", "Settings & Age Limit", "⚙", "Operations"));
        }
        nav.add(NavItem.of("/docs/GAT-2027-User-Manual.pdf", "User Manual (PDF)", "▣", "Help").asExternal());
        nav.add(NavItem.of("/dashboard", "My Member Dashboard", "▣", "Shortcuts"));
        nav.add(NavItem.of("/", "Public Website", "⌂", "Shortcuts"));
        String stateName = u.getStateId() == null ? null : states.findById(u.getStateId()).map(s -> s.getName()).orElse(null);
        String zoneName = u.getZoneId() == null ? null : zones.findById(u.getZoneId()).map(z -> z.getName()).orElse(null);
        String lgaName = u.getLgaId() == null ? null : lgas.findById(u.getLgaId()).map(l -> l.getName()).orElse(null);
        String wardName = u.getWardId() == null ? null : wards.findById(u.getWardId()).map(w -> w.getName()).orElse(null);
        String puName = u.getPollingUnitId() == null ? null : pus.findById(u.getPollingUnitId()).map(p -> p.getName()).orElse(null);
        model.addAttribute("nav", nav);
        model.addAttribute("shellTitle", title);
        model.addAttribute("shellCrumb", admin ? "Coordination centre · All states"
            : (grandPatron || zonalCoordinator) ? "Coordination centre · " + (zoneName == null ? "Your zone" : zoneName) + " only"
            : puCoordinator ? "Coordination centre · " + (puName == null ? "Your polling unit" : puName) + " only"
            : wardCoordinator ? "Coordination centre · " + (wardName == null ? "Your ward" : wardName) + " only"
            : lgaCoordinator ? "Coordination centre · " + (lgaName == null ? "Your LGA" : lgaName) + " only"
            : "Coordination centre · " + (stateName == null ? "Your state" : stateName) + " only");
        model.addAttribute("shellSubtitle", "Coordination Centre · 2027");
        model.addAttribute("shellUserLine", admin ? "National administrator" : grandPatron ? "Grand Patron" : zonalCoordinator ? "Zonal Coordinator"
            : puCoordinator ? "Polling Unit Coordinator" : wardCoordinator ? "Ward Coordinator" : lgaCoordinator ? "LGA Coordinator" : "State coordinator");
        model.addAttribute("me", u);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("stateLocked", stateLocked);
        model.addAttribute("lgaLocked", lgaLocked);
        model.addAttribute("wardLocked", wardLocked);
        model.addAttribute("puLocked", puLocked);
        model.addAttribute("canManageEvents", canManageEvents);
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
        if ((u.getRole() == Role.GRAND_PATRON || u.getRole() == Role.ZONAL_COORDINATOR) && !u.getZoneId().equals(m.getZoneId())) return "redirect:/admin/members";
        if (u.getRole() == Role.LGA_COORDINATOR && !u.getLgaId().equals(m.getLgaId())) return "redirect:/admin/members";
        if (u.getRole() == Role.WARD_COORDINATOR && !u.getWardId().equals(m.getWardId())) return "redirect:/admin/members";
        if (u.getRole() == Role.POLLING_UNIT_COORDINATOR && !u.getPollingUnitId().equals(m.getPollingUnitId())) return "redirect:/admin/members";
        model.addAttribute("p", analytics.profile(m));
        model.addAttribute("refs", analytics.referrals(m.getId()));
        model.addAttribute("self", u.getId().equals(m.getId()));
        boolean canRecommend = u.getRole() == Role.ZONAL_COORDINATOR || u.getRole() == Role.COORDINATOR;
        List<Role> proposableRoles = !canRecommend ? List.of()
            : Arrays.stream(Role.values()).filter(r -> r.coordinatorRank() > 0 && r.coordinatorRank() < u.getRole().coordinatorRank()).toList();
        model.addAttribute("canPropose", !proposableRoles.isEmpty() && !u.getId().equals(m.getId()) && m.getPendingRole() == null);
        model.addAttribute("proposableRoles", proposableRoles);
        if (m.getPendingRole() != null) {
            model.addAttribute("pendingRequestedBy", members.findById(m.getPendingRoleRequestedBy()).map(Member::getDisplayName).orElse("someone"));
            model.addAttribute("pendingStageLabel", m.getPendingRoleStage() == PromotionStage.ZONAL ? "the Zonal Coordinator" : "the National Coordinator");
            boolean canDecidePending = u.isAdmin() || (u.getRole() == Role.ZONAL_COORDINATOR && m.getPendingRoleStage() == PromotionStage.ZONAL && Objects.equals(u.getZoneId(), m.getZoneId()));
            model.addAttribute("canDecidePending", canDecidePending);
        }
        return "admin/member";
    }

    @PostMapping("/members/{id}/role")
    public String setRole(@PathVariable Long id, @RequestParam String role, RedirectAttributes ra) {
        try { memberService.setRole(currentUser.require(), id, Role.valueOf(role)); ra.addFlashAttribute("success", "Role updated."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/members/" + id;
    }

    @PostMapping("/members/{id}/role/request")
    public String requestPromotion(@PathVariable Long id, @RequestParam String role, RedirectAttributes ra) {
        try { memberService.requestPromotion(currentUser.require(), id, Role.valueOf(role)); ra.addFlashAttribute("success", "Promotion proposed."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/members/" + id;
    }

    @GetMapping("/promotions")
    public String promotionsList(@ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "Pending Promotions", f);
        if (!u.isAdmin() && u.getRole() != Role.ZONAL_COORDINATOR) return "redirect:/admin";
        List<Member> pending = u.isAdmin() ? members.findByPendingRoleIsNotNullOrderByIdDesc()
            : members.findByPendingRoleIsNotNullAndZoneIdAndPendingRoleStageOrderByIdDesc(u.getZoneId(), PromotionStage.ZONAL);
        model.addAttribute("rows", pending.stream().map(m -> new PromotionRow(m, members.findById(m.getPendingRoleRequestedBy()).map(Member::getDisplayName).orElse("Unknown"))).toList());
        return "admin/promotions";
    }

    @PostMapping("/promotions/{id}/approve")
    public String approvePromotion(@PathVariable Long id, RedirectAttributes ra) {
        try { memberService.approvePromotion(currentUser.require(), id); ra.addFlashAttribute("success", "Promotion approved."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/promotions";
    }

    @PostMapping("/promotions/{id}/reject")
    public String rejectPromotion(@PathVariable Long id, RedirectAttributes ra) {
        try { memberService.rejectPromotion(currentUser.require(), id); ra.addFlashAttribute("success", "Promotion request rejected."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/promotions";
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
        Member u = shell(model, "Events", f);
        List<Event> list = u.isAdmin() ? events.all()
            : u.getRole() == Role.ZONAL_COORDINATOR ? events.forZone(u.getZoneId())
            : u.getRole() == Role.COORDINATOR ? events.forState(u.getStateId())
            : List.of();
        model.addAttribute("rows", list.stream().map(e -> new EventRow(e, scopeLabel(e))).toList());
        return "admin/events";
    }

    @GetMapping("/events/new")
    public String newEventForm(@ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "New event", f);
        model.addAttribute("e", new Event());
        model.addAttribute("scopeLabel", scopeLabel(u.getZoneId(), u.getStateId()));
        return "admin/event-form";
    }

    @GetMapping("/events/{id}/edit")
    public String editEventForm(@PathVariable Long id, @ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "Edit event", f);
        Event e = events.find(id).orElse(null);
        if (e == null || !canManage(u, e)) return "redirect:/admin/events";
        model.addAttribute("e", e);
        model.addAttribute("scopeLabel", scopeLabel(e));
        model.addAttribute("photos", events.photosFor(id));
        return "admin/event-form";
    }

    @PostMapping("/events")
    public String createEvent(@RequestParam String title, @RequestParam LocalDate eventDate, @RequestParam(required = false) String location,
                               @RequestParam String description, @RequestParam(required = false) String published,
                               @RequestParam(required = false) String videoData, HttpServletRequest request, RedirectAttributes ra) {
        Member u = currentUser.require();
        Long zoneId = u.isAdmin() ? null : u.getRole() == Role.ZONAL_COORDINATOR ? u.getZoneId() : u.getRole() == Role.COORDINATOR ? u.getZoneId() : null;
        Long stateId = u.getRole() == Role.COORDINATOR ? u.getStateId() : null;
        try {
            Event e = events.save(null, title, eventDate, location, description, published != null, zoneId, stateId);
            events.addPhotos(e.getId(), photosFrom(request));
            events.setVideo(e.getId(), videoData);
            ra.addFlashAttribute("success", "Event created.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/events";
    }

    @PostMapping("/events/{id}")
    public String updateEvent(@PathVariable Long id, @RequestParam String title, @RequestParam LocalDate eventDate, @RequestParam(required = false) String location,
                               @RequestParam String description, @RequestParam(required = false) String published,
                               @RequestParam(required = false) String videoData, HttpServletRequest request, RedirectAttributes ra) {
        Member u = currentUser.require();
        Event existing = events.find(id).orElse(null);
        if (existing == null || !canManage(u, existing)) { ra.addFlashAttribute("error", "You don't have permission to edit this event."); return "redirect:/admin/events"; }
        try {
            events.save(id, title, eventDate, location, description, published != null, null, null);
            events.addPhotos(id, photosFrom(request));
            events.setVideo(id, videoData);
            ra.addFlashAttribute("success", "Event updated.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/events/" + id + "/edit";
    }

    @PostMapping("/events/{id}/video/delete")
    public String deleteEventVideo(@PathVariable Long id, RedirectAttributes ra) {
        Member u = currentUser.require();
        Event e = events.find(id).orElse(null);
        if (e == null || !canManage(u, e)) { ra.addFlashAttribute("error", "You don't have permission to edit this event."); return "redirect:/admin/events"; }
        try { events.clearVideo(id); ra.addFlashAttribute("success", "Video removed."); }
        catch (Exception ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/events/" + id + "/edit";
    }

    /** Reads the raw "photos" values off the request instead of binding a List<String>, because Spring's
     *  StringToCollectionConverter comma-splits a single-occurrence param (a data: URL has a comma in it). */
    private List<String> photosFrom(HttpServletRequest request) {
        String[] values = request.getParameterValues("photos");
        return values == null ? List.of() : Arrays.asList(values);
    }

    @PostMapping("/events/photos/{photoId}/delete")
    public String deleteEventPhoto(@PathVariable Long photoId, @RequestParam Long eventId, RedirectAttributes ra) {
        Member u = currentUser.require();
        Event e = events.find(eventId).orElse(null);
        if (e == null || !canManage(u, e)) { ra.addFlashAttribute("error", "You don't have permission to edit this event."); return "redirect:/admin/events"; }
        try { events.deletePhoto(photoId); ra.addFlashAttribute("success", "Photo removed."); }
        catch (Exception ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/events/" + eventId + "/edit";
    }

    @PostMapping("/events/{id}/delete")
    public String deleteEvent(@PathVariable Long id, RedirectAttributes ra) {
        Member u = currentUser.require();
        Event e = events.find(id).orElse(null);
        if (e == null || !canManage(u, e)) { ra.addFlashAttribute("error", "You don't have permission to delete this event."); return "redirect:/admin/events"; }
        try { events.delete(id); ra.addFlashAttribute("success", "Event deleted."); }
        catch (Exception ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/events";
    }

    /** Admins manage every event; a Zonal Coordinator only events in their own zone; a State Coordinator only events in their own state. */
    private boolean canManage(Member u, Event e) {
        if (u.isAdmin()) return true;
        if (u.getRole() == Role.ZONAL_COORDINATOR) return Objects.equals(u.getZoneId(), e.getZoneId());
        if (u.getRole() == Role.COORDINATOR) return Objects.equals(u.getStateId(), e.getStateId());
        return false;
    }

    private String scopeLabel(Event e) { return scopeLabel(e.getZoneId(), e.getStateId()); }

    private String scopeLabel(Long zoneId, Long stateId) {
        if (stateId != null) return states.findById(stateId).map(s -> s.getName() + " State").orElse("A state");
        if (zoneId != null) return zones.findById(zoneId).map(z -> z.getName() + " zone (all states)").orElse("A zone");
        return "Nationwide";
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
