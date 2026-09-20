package ng.gat2027.grassroot.api;

import jakarta.servlet.http.HttpServletResponse;
import ng.gat2027.grassroot.domain.Event;
import ng.gat2027.grassroot.domain.Institution;
import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.repo.*;
import ng.gat2027.grassroot.security.CurrentUser;
import ng.gat2027.grassroot.service.AnalyticsService;
import ng.gat2027.grassroot.service.CsvImportService;
import ng.gat2027.grassroot.service.EventService;
import ng.gat2027.grassroot.service.InstitutionService;
import ng.gat2027.grassroot.web.forms.AdminFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** JSON / CSV endpoints used by the location pickers, the map and exports. */
@RestController
@RequestMapping("/api")
public class ApiController {
    public record Opt(Long id, String name, String code, Double latitude, Double longitude) {}
    public record EventDto(Long id, String title, String slug, LocalDate date, String location, String description,
                            String videoUrl, boolean hasMedia, boolean upcoming) {}

    private final ZoneRepository zones; private final StateRepository states; private final LgaRepository lgas; private final WardRepository wards; private final PollingUnitRepository pus;
    private final MemberRepository members; private final CurrentUser currentUser; private final AnalyticsService analytics; private final CsvImportService importer; private final EventService events;
    private final InstitutionService institutions;

    public ApiController(ZoneRepository zones, StateRepository states, LgaRepository lgas, WardRepository wards, PollingUnitRepository pus, MemberRepository members, CurrentUser currentUser, AnalyticsService analytics, CsvImportService importer, EventService events, InstitutionService institutions) {
        this.zones = zones; this.states = states; this.lgas = lgas; this.wards = wards; this.pus = pus; this.members = members; this.currentUser = currentUser; this.analytics = analytics; this.importer = importer; this.events = events;
        this.institutions = institutions;
    }

    /** Published events for the public site (e.g. the Next.js home page). */
    @GetMapping("/events")
    public List<EventDto> events() {
        return events.published().stream().map(e -> new EventDto(e.getId(), e.getTitle(), e.getSlug(), e.getEventDate(), e.getLocation(), e.getDescription(),
            e.hasUploadedVideo() ? "/events/" + e.getId() + "/video" : null, e.hasMedia(), e.isUpcoming())).toList();
    }

    @GetMapping("/locations/zones")
    public List<Opt> zones() { return zones.findAllByOrderByNameAsc().stream().map(z -> new Opt(z.getId(), z.getName(), z.getCode(), null, null)).toList(); }

    @GetMapping("/locations/states")
    public List<Opt> states(@RequestParam(name = "zone_id", required = false) Long zoneId) {
        return (zoneId == null ? states.findAllByOrderByNameAsc() : states.findByZoneIdOrderByNameAsc(zoneId)).stream().map(s -> new Opt(s.getId(), s.getName(), s.getCode(), null, null)).toList();
    }

    @GetMapping("/locations/lgas")
    public List<Opt> lgas(@RequestParam(name = "state_id", required = false) Long stateId) {
        return stateId == null ? List.of() : lgas.findByStateIdOrderByNameAsc(stateId).stream().map(l -> new Opt(l.getId(), l.getName(), l.getCode(), null, null)).toList();
    }

    @GetMapping("/locations/wards")
    public List<Opt> wards(@RequestParam(name = "lga_id", required = false) Long lgaId) {
        return lgaId == null ? List.of() : wards.findByLgaIdOrderByNameAsc(lgaId).stream().map(w -> new Opt(w.getId(), w.getName(), w.getCode(), null, null)).toList();
    }

    @GetMapping("/locations/polling-units")
    public List<Opt> pollingUnits(@RequestParam(name = "ward_id", required = false) Long wardId) {
        return wardId == null ? List.of() : pus.findByWardIdOrderByCodeAscNameAsc(wardId).stream().map(p -> new Opt(p.getId(), p.getName(), p.getCode(), p.getLatitude(), p.getLongitude())).toList();
    }

    @GetMapping("/institutions")
    public List<Opt> institutions(@RequestParam String type, @RequestParam String ownership) {
        return institutions.find(type, ownership).stream().map(i -> new Opt(i.getId(), i.getName(), null, null, null)).toList();
    }

    @GetMapping("/referral/{code}")
    public ResponseEntity<Map<String, String>> referral(@PathVariable String code) {
        return members.findByReferralCodeIgnoreCase(code).filter(Member::isActive)
            .map(m -> ResponseEntity.ok(Map.of("name", m.getDisplayName(), "memberCode", m.getMemberCode())))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Referral code not found")));
    }

    @GetMapping("/admin/map")
    public List<AnalyticsService.MapPoint> map(@ModelAttribute AdminFilter f) {
        Member u = currentUser.require();
        if (!u.isAdmin()) { f.setZoneId(u.getZoneId()); f.setStateId(u.getStateId()); }
        return analytics.mapPoints(u, f);
    }

    @GetMapping("/admin/members.csv")
    public void exportCsv(@ModelAttribute AdminFilter f, HttpServletResponse response) throws IOException {
        Member u = currentUser.require();
        if (!u.isAdmin()) { f.setZoneId(u.getZoneId()); f.setStateId(u.getStateId()); }
        List<Map<String, Object>> rows = analytics.exportRows(u, f);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"gat-members-" + LocalDate.now() + ".csv\"");
        PrintWriter out = response.getWriter();
        out.print('﻿');
        List<String> cols = rows.isEmpty() ? List.of("member_code") : rows.get(0).keySet().stream().map(k -> k.toLowerCase()).toList();
        out.println(String.join(",", cols));
        for (Map<String, Object> r : rows) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (Object v : r.values()) { if (i++ > 0) sb.append(','); sb.append(csv(v)); }
            out.println(sb);
        }
        out.flush();
    }

    private static String csv(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        return s.matches("(?s).*[\",\\n\\r].*") ? '"' + s.replace("\"", "\"\"") + '"' : s;
    }

    @PostMapping(value = "/admin/import", consumes = "text/csv")
    public ResponseEntity<?> importCsv(@RequestBody String csv) {
        Member u = currentUser.require();
        if (!u.isAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        try {
            CsvImportService.Summary s = importer.importCsv(csv);
            return ResponseEntity.ok(Map.of("rows", s.rows, "wardsCreated", s.wardsCreated, "pusCreated", s.pusCreated, "pusUpdated", s.pusUpdated, "badCoords", s.badCoords, "skipped", s.skipped.size(), "lgasCreated", s.lgasCreated, "text", s.text()));
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }
}
