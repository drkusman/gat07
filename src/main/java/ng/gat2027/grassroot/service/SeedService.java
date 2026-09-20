package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.*;
import ng.gat2027.grassroot.repo.*;
import ng.gat2027.grassroot.seed.NigeriaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** On every start: seed zones / states / LGAs and the default admin (idempotent), then import polling units if the table is empty. */
@Component
public class SeedService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private final ZoneRepository zones; private final StateRepository states; private final LgaRepository lgas; private final PollingUnitRepository pus;
    private final MemberRepository members; private final PasswordEncoder encoder; private final CsvImportService importer; private final ResourceLoader resources;
    private final RoleResponsibilityRepository roleResponsibilities;
    @Value("${gat.admin.phone}") String adminPhone;
    @Value("${gat.admin.password}") String adminPassword;
    @Value("${gat.import.csv}") String csvLocation;
    @Value("${gat.import.on-startup}") boolean importOnStartup;

    public SeedService(ZoneRepository zones, StateRepository states, LgaRepository lgas, PollingUnitRepository pus, MemberRepository members, PasswordEncoder encoder, CsvImportService importer,
                        ResourceLoader resources, RoleResponsibilityRepository roleResponsibilities) {
        this.zones = zones; this.states = states; this.lgas = lgas; this.pus = pus; this.members = members; this.encoder = encoder; this.importer = importer; this.resources = resources;
        this.roleResponsibilities = roleResponsibilities;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        seedLocations();
        seedAdmin();
        seedDemoAccounts();
        seedRoleResponsibilities();
        if (importOnStartup && pus.count() == 0) {
            Resource csv = resources.getResource(csvLocation);
            if (csv.exists()) {
                log.info("[setup] polling_units is empty — importing {} ...", csvLocation);
                long t0 = System.currentTimeMillis();
                String text = new String(csv.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                var s = importer.importCsv(text);
                log.info("[setup] {} in {}s", s.text(), (System.currentTimeMillis() - t0) / 1000);
            } else log.warn("[setup] polling units CSV not found at {}", csvLocation);
        }
    }

    @Transactional
    public void seedLocations() {
        if (states.count() > 0) return;
        Map<String, Long> zoneIds = new HashMap<>();
        for (var z : NigeriaData.ZONES) { Zone e = new Zone(); e.setCode(z.code()); e.setName(z.name()); zoneIds.put(z.code(), zones.save(e).getId()); }
        int nLgas = 0;
        for (var s : NigeriaData.STATES) {
            State st = new State(); st.setZoneId(zoneIds.get(s.zone())); st.setCode(s.code()); st.setName(s.name()); st.setCapital(s.capital());
            Long sid = states.save(st).getId();
            int i = 0;
            for (String name : s.lgas()) { Lga l = new Lga(); l.setStateId(sid); l.setCode(String.format("%02d", ++i)); l.setName(name); lgas.save(l); nLgas++; }
        }
        log.info("[seed] {} zones, {} states, {} LGAs", NigeriaData.ZONES.size(), NigeriaData.STATES.size(), nLgas);
    }

    @Transactional
    public void seedAdmin() {
        if (members.existsByRole(Role.ADMIN)) return;
        Member m = new Member();
        m.setMemberCode("GAT-000001"); m.setReferralCode(Codes.make("ADMIN", 6)); m.setRole(Role.ADMIN);
        m.setFirstName("System"); m.setLastName("Administrator"); m.setPhone(Codes.normalizePhone(adminPhone));
        m.setPasswordHash(encoder.encode(adminPassword));
        members.save(m);
        log.info("[seed] default admin created -> phone {} / password {} (change it after first login)", adminPhone, adminPassword);
    }

    /** Demo accounts so every role can be tried out without manual setup. Idempotent by phone. */
    @Transactional
    public void seedDemoAccounts() {
        Long ncZoneId = zones.findByCode("NC").map(Zone::getId).orElse(null);
        Long benueId = ncZoneId == null ? null : states.findByZoneIdOrderByNameAsc(ncZoneId).stream()
            .filter(s -> s.getName().equals("Benue")).map(State::getId).findFirst().orElse(null);

        String coordPhone = Codes.normalizePhone("08000000002");
        if (benueId != null && !members.existsByPhone(coordPhone)) {
            Member m = new Member();
            m.setMemberCode(Codes.memberCode(members.maxId() + 1));
            m.setReferralCode(Codes.make("COORD", 6));
            m.setRole(Role.COORDINATOR);
            m.setFirstName("Demo"); m.setLastName("Coordinator");
            m.setZoneId(ncZoneId); m.setStateId(benueId);
            m.setPhone(coordPhone);
            m.setPasswordHash(encoder.encode("coord1234"));
            members.save(m);
            log.info("[seed] demo state coordinator created -> phone {} / password coord1234 (Benue State)", coordPhone);
        }

        String patronPhone = Codes.normalizePhone("08000000003");
        if (ncZoneId != null && !members.existsByPhone(patronPhone)) {
            Member m = new Member();
            m.setMemberCode(Codes.memberCode(members.maxId() + 1));
            m.setReferralCode(Codes.make("PATRON", 6));
            m.setRole(Role.GRAND_PATRON);
            m.setFirstName("Demo"); m.setLastName("Grand Patron");
            m.setZoneId(ncZoneId);
            m.setPhone(patronPhone);
            m.setPasswordHash(encoder.encode("patron1234"));
            members.save(m);
            log.info("[seed] demo grand patron created -> phone {} / password patron1234 (North Central zone)", patronPhone);
        }

        String zonalPhone = Codes.normalizePhone("08000000005");
        if (ncZoneId != null && !members.existsByPhone(zonalPhone)) {
            Member m = new Member();
            m.setMemberCode(Codes.memberCode(members.maxId() + 1));
            m.setReferralCode(Codes.make("ZONAL", 6));
            m.setRole(Role.ZONAL_COORDINATOR);
            m.setFirstName("Demo"); m.setLastName("Zonal Coordinator");
            m.setZoneId(ncZoneId);
            m.setPhone(zonalPhone);
            m.setPasswordHash(encoder.encode("zonal1234"));
            members.save(m);
            log.info("[seed] demo zonal coordinator created -> phone {} / password zonal1234 (North Central zone)", zonalPhone);
        }

        String memberPhone = Codes.normalizePhone("08000000004");
        if (benueId != null && !members.existsByPhone(memberPhone)) {
            Member m = new Member();
            m.setMemberCode(Codes.memberCode(members.maxId() + 1));
            m.setReferralCode(Codes.make("MEMBER", 6));
            m.setRole(Role.MEMBER);
            m.setFirstName("Demo"); m.setLastName("Member");
            m.setZoneId(ncZoneId); m.setStateId(benueId);
            m.setPhone(memberPhone);
            m.setPasswordHash(encoder.encode("member1234"));
            members.save(m);
            log.info("[seed] demo member created -> phone {} / password member1234 (Benue State)", memberPhone);
        }
    }

    /** Starting set of "current responsibilities" per role, drawn from what each role can actually do in the
     *  system today (dashboard nav, admin nav, data scoping, promotion rules). Admins can edit/revoke these
     *  freely afterwards from Admin > Roles & Responsibilities - this only seeds the table when it's empty. */
    @Transactional
    public void seedRoleResponsibilities() {
        if (roleResponsibilities.count() > 0) return;
        record R(Role role, String text) {}
        var seed = java.util.List.of(
            new R(Role.MEMBER, "Keep your polling unit, biodata, and profile information up to date."),
            new R(Role.MEMBER, "Refer new members using your personal referral code and track your referral network."),
            new R(Role.MEMBER, "File field reports about activities or issues at your polling unit."),

            new R(Role.POLLING_UNIT_COORDINATOR, "Everything a Member does, plus oversight of your own polling unit."),
            new R(Role.POLLING_UNIT_COORDINATOR, "View Coordination Centre data (members, field reports, coverage) scoped to your polling unit."),
            new R(Role.POLLING_UNIT_COORDINATOR, "Be considered for promotion by your Ward, LGA, State, or Zonal Coordinator."),

            new R(Role.WARD_COORDINATOR, "Oversee Coordination Centre data (members, field reports, coverage) for your entire ward."),
            new R(Role.WARD_COORDINATOR, "Support and coordinate Polling Unit Coordinators within your ward."),

            new R(Role.LGA_COORDINATOR, "Oversee Coordination Centre data (members, field reports, coverage) for your entire Local Government Area."),
            new R(Role.LGA_COORDINATOR, "Support and coordinate Ward Coordinators within your LGA."),

            new R(Role.COORDINATOR, "Oversee Coordination Centre data (members, field reports, coverage) for your entire state."),
            new R(Role.COORDINATOR, "Propose promotions (up to LGA/Ward/Polling Unit Coordinator) for members in your state; proposals route through Zonal, then National approval."),
            new R(Role.COORDINATOR, "Create and manage Events for your state."),

            new R(Role.ZONAL_COORDINATOR, "Oversee Coordination Centre data (members, field reports, coverage) for your entire zone."),
            new R(Role.ZONAL_COORDINATOR, "Propose promotions (up to State Coordinator) for members in your zone; proposals go straight to National approval."),
            new R(Role.ZONAL_COORDINATOR, "Approve or advance State Coordinators' promotion proposals from your zone to the National stage."),
            new R(Role.ZONAL_COORDINATOR, "Create and manage Events for your zone."),

            new R(Role.GRAND_PATRON, "View Coordination Centre data (members, field reports, coverage) for your zone in an oversight capacity."),
            new R(Role.GRAND_PATRON, "No promotion-approval, event-management, or member-editing responsibilities."),

            new R(Role.MEDIA_COORDINATOR, "Create and submit Events for Admin approval."),
            new R(Role.MEDIA_COORDINATOR, "Create and submit Home Page promotional videos for Admin approval."),
            new R(Role.MEDIA_COORDINATOR, "Coordination Centre access is limited to your own event and video submissions."),

            new R(Role.ADMIN, "Full national visibility across all Coordination Centre data (members, field reports, coverage, referrals)."),
            new R(Role.ADMIN, "Manage member roles, account status (suspend/reactivate), and reset member passwords."),
            new R(Role.ADMIN, "Approve or reject promotion requests at any stage; can directly promote or demote any member."),
            new R(Role.ADMIN, "Approve Events and Home Page videos submitted by Coordinators and Media Coordinators."),
            new R(Role.ADMIN, "Manage Announcements, Location Data, Institutions, Settings & age limits, and Roles & Responsibilities records.")
        );
        for (R r : seed) {
            RoleResponsibility e = new RoleResponsibility();
            e.setRole(r.role()); e.setDescription(r.text());
            roleResponsibilities.save(e);
        }
        log.info("[seed] {} role responsibility entries created", seed.size());
    }
}
