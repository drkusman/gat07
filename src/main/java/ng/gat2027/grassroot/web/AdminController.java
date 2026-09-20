package ng.gat2027.grassroot.web;

import ng.gat2027.grassroot.domain.Announcement;
import ng.gat2027.grassroot.domain.AnnouncementAudience;
import ng.gat2027.grassroot.domain.Committee;
import ng.gat2027.grassroot.domain.Event;
import ng.gat2027.grassroot.domain.Institution;
import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.domain.Position;
import ng.gat2027.grassroot.domain.MemberStatus;
import ng.gat2027.grassroot.domain.PromotionStage;
import ng.gat2027.grassroot.domain.PromoVideo;
import ng.gat2027.grassroot.domain.ReportStatus;
import ng.gat2027.grassroot.domain.Role;
import ng.gat2027.grassroot.repo.*;
import ng.gat2027.grassroot.security.CurrentUser;
import ng.gat2027.grassroot.service.*;
import ng.gat2027.grassroot.web.forms.AdminFilter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/admin")
public class AdminController {
    public record EventRow(Event event, String scopeLabel) {}
    public record PromotionRow(Member member, String requestedByName) {}
    public record AnnouncementRow(Announcement announcement, String scopeLabel) {}

    private final CurrentUser currentUser; private final AnalyticsService analytics; private final MemberService memberService; private final ReportService reportService;
    private final SettingsService settings; private final CsvImportService importer; private final MemberRepository members; private final EventService events;
    private final ZoneRepository zones; private final StateRepository states; private final LgaRepository lgas; private final WardRepository wards; private final PollingUnitRepository pus;
    private final AnnouncementService announcements;
    private final PromoVideoService promoVideos;
    private final InstitutionService institutions;
    private final RoleResponsibilityService roleResponsibilities;
    private final OrganizationService organization;
    private final AppointmentLetterService letters;

    public AdminController(CurrentUser currentUser, AnalyticsService analytics, MemberService memberService, ReportService reportService, SettingsService settings, CsvImportService importer,
                           MemberRepository members, EventService events, ZoneRepository zones, StateRepository states, LgaRepository lgas, WardRepository wards, PollingUnitRepository pus,
                           AnnouncementService announcements, PromoVideoService promoVideos, InstitutionService institutions, RoleResponsibilityService roleResponsibilities, OrganizationService organization,
                           AppointmentLetterService letters) {
        this.currentUser = currentUser; this.analytics = analytics; this.memberService = memberService; this.reportService = reportService; this.settings = settings; this.importer = importer;
        this.members = members; this.events = events; this.zones = zones; this.states = states; this.lgas = lgas; this.wards = wards; this.pus = pus; this.announcements = announcements;
        this.promoVideos = promoVideos; this.institutions = institutions; this.roleResponsibilities = roleResponsibilities; this.organization = organization; this.letters = letters;
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
        boolean mediaCoordinator = u.getRole().isMediaTeam();
        boolean stateLocked = stateCoordinator || lgaCoordinator || wardCoordinator || puCoordinator;
        boolean lgaLocked = lgaCoordinator || wardCoordinator || puCoordinator;
        boolean wardLocked = wardCoordinator || puCoordinator;
        boolean puLocked = puCoordinator;
        boolean canManageEvents = admin || zonalCoordinator || stateCoordinator || mediaCoordinator;
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
        if (canManageEvents) {
            NavItem eventsNav = NavItem.of("/admin/events", "Events", "◈", "Operations");
            if (admin) eventsNav = eventsNav.withCount(events.countPendingApproval());
            nav.add(eventsNav);
        }
        if (admin || zonalCoordinator) {
            long pendingCount = admin ? members.countByPendingRoleIsNotNull()
                : members.countByPendingRoleIsNotNullAndZoneIdAndPendingRoleStage(u.getZoneId(), PromotionStage.ZONAL);
            nav.add(NavItem.of("/admin/promotions", "Pending Promotions", "★", "Operations").withCount(pendingCount));
        }
        if (admin || mediaCoordinator) {
            NavItem videosNav = NavItem.of("/admin/videos", "Home Videos", "▶", "Operations");
            if (admin) videosNav = videosNav.withCount(promoVideos.countPendingApproval());
            nav.add(videosNav);
        }
        if (admin) {
            nav.add(NavItem.of("/admin/announcements", "Announcements", "📣", "Operations"));
            nav.add(NavItem.of("/admin/members/suspended", "Suspended Members", "⛔", "Operations").withCount(members.countByStatus(MemberStatus.SUSPENDED)));
            nav.add(NavItem.of("/admin/locations", "Location Data", "⌖", "Operations"));
            nav.add(NavItem.of("/admin/institutions", "Institutions", "🎓", "Operations"));
            nav.add(NavItem.of("/admin/roles", "Roles & Responsibilities", "🛡", "Operations"));
            nav.add(NavItem.of("/admin/organization", "Organizational Structure", "🏛", "Operations"));
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
            : mediaCoordinator ? "Coordination centre · Your submissions"
            : (grandPatron || zonalCoordinator) ? "Coordination centre · " + (zoneName == null ? "Your zone" : zoneName) + " only"
            : puCoordinator ? "Coordination centre · " + (puName == null ? "Your polling unit" : puName) + " only"
            : wardCoordinator ? "Coordination centre · " + (wardName == null ? "Your ward" : wardName) + " only"
            : lgaCoordinator ? "Coordination centre · " + (lgaName == null ? "Your LGA" : lgaName) + " only"
            : "Coordination centre · " + (stateName == null ? "Your state" : stateName) + " only");
        model.addAttribute("shellSubtitle", "Coordination Centre · 2027");
        model.addAttribute("shellUserLine", admin ? "National administrator" : u.getRole() == Role.NATIONAL_PUBLICITY_SECRETARY ? "National Publicity Secretary"
            : mediaCoordinator ? "Media Coordinator" : grandPatron ? "Grand Patron" : zonalCoordinator ? "Zonal Coordinator"
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

    public record SuspendedRow(Member member, String location) {}

    @GetMapping("/members/suspended")
    public String suspendedMembers(@ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "Suspended Members", f);
        if (!u.isAdmin()) return "redirect:/admin";
        List<Member> suspended = members.findByStatusOrderByUpdatedAtDesc(MemberStatus.SUSPENDED);
        model.addAttribute("rows", suspended.stream().map(m -> new SuspendedRow(m, locationLabel(m))).toList());
        return "admin/suspended";
    }

    private String locationLabel(Member m) {
        String state = m.getStateId() == null ? null : states.findById(m.getStateId()).map(s -> s.getName()).orElse(null);
        if (state != null) return state;
        String zone = m.getZoneId() == null ? null : zones.findById(m.getZoneId()).map(z -> z.getName()).orElse(null);
        return zone != null ? zone + " zone" : "—";
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
        if (u.isAdmin()) {
            model.addAttribute("committeeGroups", organization.positionsGroupedByCommittee());
            Position currentPosition = m.getPositionId() == null ? null : organization.findPosition(m.getPositionId()).orElse(null);
            model.addAttribute("currentPositionLabel", currentPosition == null ? null
                : currentPosition.getTitle() + " (" + organization.findCommittee(currentPosition.getCommitteeId()).map(Committee::getCode).orElse("?") + ")");
        }
        return "admin/member";
    }

    @PostMapping("/members/{id}/role")
    public String setRole(@PathVariable Long id, @RequestParam String role, RedirectAttributes ra) {
        try { memberService.setRole(currentUser.require(), id, Role.valueOf(role)); ra.addFlashAttribute("success", "Role updated."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/members/" + id;
    }

    @PostMapping("/members/{id}/position")
    public String assignPosition(@PathVariable Long id, @RequestParam(required = false) Long positionId, RedirectAttributes ra) {
        try { memberService.assignPosition(currentUser.require(), id, positionId); ra.addFlashAttribute("success", "Position updated."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/members/" + id;
    }

    @GetMapping("/members/{id}/appointment-letter")
    public void appointmentLetter(@PathVariable Long id, HttpServletResponse response) throws IOException {
        try {
            Member m = members.findById(id).orElseThrow(() -> new MemberService.MemberException("Member not found"));
            if (m.getPositionId() == null) throw new MemberService.MemberException("This member has no position assigned yet");
            Position position = organization.findPosition(m.getPositionId()).orElseThrow(() -> new MemberService.MemberException("Position not found"));
            Committee committee = organization.findCommittee(position.getCommitteeId()).orElseThrow(() -> new MemberService.MemberException("Committee not found"));
            byte[] pdf = letters.generate(m, position, committee);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"Appointment-Letter-" + m.getMemberCode() + ".pdf\"");
            response.setContentLength(pdf.length);
            response.getOutputStream().write(pdf);
            response.getOutputStream().flush();
        } catch (MemberService.MemberException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        }
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
    public String setStatus(@PathVariable Long id, @RequestParam String status, @RequestParam(required = false) String back, RedirectAttributes ra) {
        try { memberService.setStatus(currentUser.require(), id, MemberStatus.valueOf(status)); ra.addFlashAttribute("success", "Status updated."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:" + (back != null && back.startsWith("/admin/") ? back : "/admin/members/" + id);
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
            : u.getRole().isMediaTeam() ? events.forCreator(u.getId())
            : List.of();
        model.addAttribute("rows", list.stream().map(e -> new EventRow(e, scopeLabel(e))).toList());
        return "admin/events";
    }

    @PostMapping("/events/{id}/approve")
    public String approveEvent(@PathVariable Long id, RedirectAttributes ra) {
        try { events.approve(id); ra.addFlashAttribute("success", "Event approved and now visible on the public site."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/events";
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
        boolean approved = !u.getRole().isMediaTeam();
        try {
            Event e = events.save(null, title, eventDate, location, description, published != null, zoneId, stateId, u.getId(), approved);
            events.addPhotos(e.getId(), photosFrom(request));
            events.setVideo(e.getId(), videoData);
            ra.addFlashAttribute("success", approved ? "Event created." : "Event submitted — it will appear on the public site once Admin approves it.");
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
        boolean approved = !u.getRole().isMediaTeam();
        try {
            events.save(id, title, eventDate, location, description, published != null, null, null, null, approved);
            events.addPhotos(id, photosFrom(request));
            events.setVideo(id, videoData);
            ra.addFlashAttribute("success", approved ? "Event updated." : "Event updated — it will need Admin re-approval before it's visible again.");
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

    /** Admins manage every event; a Zonal Coordinator only events in their own zone; a State Coordinator only events in
     *  their own state; a Media Coordinator or National Publicity Secretary only the events they personally submitted. */
    private boolean canManage(Member u, Event e) {
        if (u.isAdmin()) return true;
        if (u.getRole() == Role.ZONAL_COORDINATOR) return Objects.equals(u.getZoneId(), e.getZoneId());
        if (u.getRole() == Role.COORDINATOR) return Objects.equals(u.getStateId(), e.getStateId());
        if (u.getRole().isMediaTeam()) return Objects.equals(u.getId(), e.getCreatedBy());
        return false;
    }

    private String scopeLabel(Event e) { return scopeLabel(e.getZoneId(), e.getStateId()); }

    private String scopeLabel(Long zoneId, Long stateId) {
        if (stateId != null) return states.findById(stateId).map(s -> s.getName() + " State").orElse("A state");
        if (zoneId != null) return zones.findById(zoneId).map(z -> z.getName() + " zone (all states)").orElse("A zone");
        return "Nationwide";
    }

    @GetMapping("/announcements")
    public String announcementsList(@ModelAttribute AdminFilter f, Model model) {
        shell(model, "Announcements", f);
        model.addAttribute("rows", announcements.all().stream().map(a -> new AnnouncementRow(a, announcementScopeLabel(a))).toList());
        return "admin/announcements";
    }

    @GetMapping("/announcements/new")
    public String newAnnouncementForm(@ModelAttribute AdminFilter f, Model model) {
        shell(model, "New announcement", f);
        Announcement a = new Announcement();
        a.setAudience(AnnouncementAudience.PUBLIC);
        model.addAttribute("a", a);
        model.addAttribute("stateOptions", states.findAllByOrderByNameAsc());
        return "admin/announcement-form";
    }

    @GetMapping("/announcements/{id}/edit")
    public String editAnnouncementForm(@PathVariable Long id, @ModelAttribute AdminFilter f, Model model) {
        shell(model, "Edit announcement", f);
        Announcement a = announcements.find(id).orElse(null);
        if (a == null) return "redirect:/admin/announcements";
        model.addAttribute("a", a);
        model.addAttribute("stateOptions", states.findAllByOrderByNameAsc());
        return "admin/announcement-form";
    }

    @PostMapping("/announcements")
    public String createAnnouncement(@RequestParam String title, @RequestParam String body, @RequestParam AnnouncementAudience audience,
                                      @RequestParam(required = false) Long stateId, @RequestParam(required = false) String published, RedirectAttributes ra) {
        try {
            announcements.save(null, title, body, audience, stateId, published != null, currentUser.require().getId());
            ra.addFlashAttribute("success", "Announcement created.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/announcements";
    }

    @PostMapping("/announcements/{id}")
    public String updateAnnouncement(@PathVariable Long id, @RequestParam String title, @RequestParam String body, @RequestParam AnnouncementAudience audience,
                                      @RequestParam(required = false) Long stateId, @RequestParam(required = false) String published, RedirectAttributes ra) {
        try {
            announcements.save(id, title, body, audience, stateId, published != null, currentUser.require().getId());
            ra.addFlashAttribute("success", "Announcement updated.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/announcements/" + id + "/edit";
    }

    @PostMapping("/announcements/{id}/delete")
    public String deleteAnnouncement(@PathVariable Long id, RedirectAttributes ra) {
        try { announcements.delete(id); ra.addFlashAttribute("success", "Announcement deleted."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/announcements";
    }

    private String announcementScopeLabel(Announcement a) {
        if (a.getAudience() == AnnouncementAudience.PUBLIC) return "Public (home page)";
        if (a.getStateId() == null) return "All members (nationwide)";
        return states.findById(a.getStateId()).map(s -> s.getName() + " members").orElse("A state's members");
    }

    @GetMapping("/videos")
    public String videosList(@ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "Home Videos", f);
        model.addAttribute("rows", u.isAdmin() ? promoVideos.list() : promoVideos.listByCreator(u.getId()));
        return "admin/videos";
    }

    @PostMapping("/videos")
    public String addVideo(@RequestParam String url, @RequestParam(required = false) String title,
                            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime expiresAt,
                            @RequestParam(required = false) Integer priorityHours, RedirectAttributes ra) {
        Member u = currentUser.require();
        boolean approved = !u.getRole().isMediaTeam();
        try {
            promoVideos.add(url, title, expiresAt, priorityHours, u.getId(), approved);
            ra.addFlashAttribute("success", approved ? "Video added to the home page loop." : "Video submitted — it will join the loop once Admin approves it.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/videos";
    }

    @GetMapping("/videos/{id}/edit")
    public String editVideoForm(@PathVariable Long id, @ModelAttribute AdminFilter f, Model model) {
        Member u = shell(model, "Edit video", f);
        PromoVideo v = promoVideos.find(id).orElse(null);
        if (v == null || !canManageVideo(u, v)) return "redirect:/admin/videos";
        model.addAttribute("v", v);
        return "admin/video-form";
    }

    @PostMapping("/videos/{id}")
    public String updateVideo(@PathVariable Long id, @RequestParam String url, @RequestParam(required = false) String title,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime expiresAt,
                               @RequestParam(required = false) Integer priorityHours, RedirectAttributes ra) {
        Member u = currentUser.require();
        PromoVideo existing = promoVideos.find(id).orElse(null);
        if (existing == null || !canManageVideo(u, existing)) { ra.addFlashAttribute("error", "You don't have permission to edit this video."); return "redirect:/admin/videos"; }
        boolean approved = !u.getRole().isMediaTeam();
        try {
            promoVideos.update(id, url, title, expiresAt, priorityHours, approved);
            ra.addFlashAttribute("success", approved ? "Video updated." : "Video updated — it will need Admin re-approval before it plays again.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/videos";
    }

    @PostMapping("/videos/{id}/approve")
    public String approveVideo(@PathVariable Long id, RedirectAttributes ra) {
        try { promoVideos.approve(id); ra.addFlashAttribute("success", "Video approved and now in the home page loop."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/videos";
    }

    @PostMapping("/videos/{id}/delete")
    public String deleteVideo(@PathVariable Long id, RedirectAttributes ra) {
        Member u = currentUser.require();
        PromoVideo v = promoVideos.find(id).orElse(null);
        if (v == null || !canManageVideo(u, v)) { ra.addFlashAttribute("error", "You don't have permission to remove this video."); return "redirect:/admin/videos"; }
        try { promoVideos.delete(id); ra.addFlashAttribute("success", "Video removed."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/videos";
    }

    /** Admin manages every video; a Media Coordinator only the ones they personally submitted. */
    private boolean canManageVideo(Member u, PromoVideo v) {
        return u.isAdmin() || Objects.equals(u.getId(), v.getCreatedBy());
    }

    @GetMapping("/institutions")
    public String institutionsList(@ModelAttribute AdminFilter f, Model model) {
        shell(model, "Institutions", f);
        model.addAttribute("rows", institutions.all());
        return "admin/institutions";
    }

    @PostMapping("/institutions")
    public String addInstitution(@RequestParam String name, @RequestParam String type, @RequestParam String ownership, RedirectAttributes ra) {
        try { institutions.add(name, type, ownership); ra.addFlashAttribute("success", "Institution added."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/institutions";
    }

    @PostMapping("/institutions/import")
    public String importInstitutions(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        try {
            if (file.isEmpty()) throw new IllegalArgumentException("Choose a CSV file first");
            var s = institutions.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
            ra.addFlashAttribute("success", s.text());
        } catch (Exception e) { ra.addFlashAttribute("error", "Import failed: " + e.getMessage()); }
        return "redirect:/admin/institutions";
    }

    @PostMapping("/institutions/{id}/update")
    public String updateInstitution(@PathVariable Long id, @RequestParam String name, @RequestParam String type, @RequestParam String ownership, RedirectAttributes ra) {
        try { institutions.update(id, name, type, ownership); ra.addFlashAttribute("success", "Institution updated."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/institutions";
    }

    @PostMapping("/institutions/{id}/delete")
    public String deleteInstitution(@PathVariable Long id, RedirectAttributes ra) {
        try { institutions.delete(id); ra.addFlashAttribute("success", "Institution removed."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/institutions";
    }

    @GetMapping("/roles")
    public String rolesList(@ModelAttribute AdminFilter f, Model model) {
        shell(model, "Roles & Responsibilities", f);
        model.addAttribute("sections", roleResponsibilities.byRole());
        return "admin/roles";
    }

    @PostMapping("/roles")
    public String addResponsibility(@RequestParam Role role, @RequestParam String description, RedirectAttributes ra) {
        try { roleResponsibilities.add(role, description); ra.addFlashAttribute("success", "Responsibility added."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/roles";
    }

    @PostMapping("/roles/{id}/edit")
    public String editResponsibility(@PathVariable Long id, @RequestParam String description, RedirectAttributes ra) {
        try { roleResponsibilities.update(id, description); ra.addFlashAttribute("success", "Responsibility updated."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/roles";
    }

    @PostMapping("/roles/{id}/revoke")
    public String revokeResponsibility(@PathVariable Long id, RedirectAttributes ra) {
        try { roleResponsibilities.revoke(id); ra.addFlashAttribute("success", "Responsibility revoked."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/roles";
    }

    @GetMapping("/organization")
    public String organizationList(@ModelAttribute AdminFilter f, Model model) {
        shell(model, "Organizational Structure", f);
        model.addAttribute("committeeRows", organization.committees());
        model.addAttribute("committeeOptions", organization.committeesList());
        model.addAttribute("positionRows", organization.positions());
        return "admin/organization";
    }

    @PostMapping("/organization/committees")
    public String addCommittee(@RequestParam String code, @RequestParam String name, RedirectAttributes ra) {
        try { organization.addCommittee(code, name); ra.addFlashAttribute("success", "Committee added."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/organization";
    }

    @PostMapping("/organization/committees/{id}/edit")
    public String editCommittee(@PathVariable Long id, @RequestParam String code, @RequestParam String name, RedirectAttributes ra) {
        try { organization.updateCommittee(id, code, name); ra.addFlashAttribute("success", "Committee updated."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/organization";
    }

    @PostMapping("/organization/committees/{id}/delete")
    public String deleteCommittee(@PathVariable Long id, RedirectAttributes ra) {
        try { organization.deleteCommittee(id); ra.addFlashAttribute("success", "Committee removed."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/organization";
    }

    @PostMapping("/organization/positions")
    public String addPosition(@RequestParam String title, @RequestParam Long committeeId, RedirectAttributes ra) {
        try { organization.addPosition(title, committeeId); ra.addFlashAttribute("success", "Position added."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/organization";
    }

    @PostMapping("/organization/positions/{id}/edit")
    public String editPosition(@PathVariable Long id, @RequestParam String title, @RequestParam Long committeeId, RedirectAttributes ra) {
        try { organization.updatePosition(id, title, committeeId); ra.addFlashAttribute("success", "Position updated."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/organization";
    }

    @PostMapping("/organization/positions/{id}/delete")
    public String deletePosition(@PathVariable Long id, RedirectAttributes ra) {
        try { organization.deletePosition(id); ra.addFlashAttribute("success", "Position removed."); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/organization";
    }

    @PostMapping("/organization/positions/import")
    public String importPositions(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        try {
            if (file.isEmpty()) throw new IllegalArgumentException("Choose a CSV file first");
            var s = organization.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
            ra.addFlashAttribute("success", s.text());
        } catch (Exception e) { ra.addFlashAttribute("error", "Import failed: " + e.getMessage()); }
        return "redirect:/admin/organization";
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
