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
    @Value("${gat.admin.phone}") String adminPhone;
    @Value("${gat.admin.password}") String adminPassword;
    @Value("${gat.import.csv}") String csvLocation;
    @Value("${gat.import.on-startup}") boolean importOnStartup;

    public SeedService(ZoneRepository zones, StateRepository states, LgaRepository lgas, PollingUnitRepository pus, MemberRepository members, PasswordEncoder encoder, CsvImportService importer, ResourceLoader resources) {
        this.zones = zones; this.states = states; this.lgas = lgas; this.pus = pus; this.members = members; this.encoder = encoder; this.importer = importer; this.resources = resources;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        seedLocations();
        seedAdmin();
        seedDemoAccounts();
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
}
