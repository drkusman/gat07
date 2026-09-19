package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.domain.Role;
import ng.gat2027.grassroot.web.forms.AdminFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Read models for dashboards. Plain JDBC with portable SQL (runs on PostgreSQL and H2 in PostgreSQL mode).
 * Every member-level query is filtered through {@link #where}, which locks each coordinator role to its own area:
 * Grand Patrons / Zonal Coordinators to their zone, State Coordinators to their state, and LGA/Ward/Polling-unit
 * Coordinators to their state plus their own LGA/ward/polling unit.
 */
@Service
public class AnalyticsService {
    private final JdbcTemplate jdbc;

    public AnalyticsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record NameCount(String name, long n) {}
    public record Referrer(long id, String memberCode, String firstName, String lastName, String stateName, long n) {}
    public record Daily(LocalDate day, long n) {}
    public record Summary(long members, long today, long week, long male, long female, long referred, long withCoords,
                          long pusCovered, long wardsCovered, long lgasCovered, long statesCovered, long openReports,
                          List<NameCount> byZone, List<NameCount> byState, List<NameCount> topLgas, List<NameCount> topWards, List<NameCount> topPus,
                          List<Daily> daily, List<Referrer> topReferrers, List<NameCount> ageBands) {}
    public record AreaRow(long id, String name, String code, long members, long male, long female) {}
    public record MemberRow(long id, String memberCode, String role, String status, String firstName, String lastName, String gender, String phone,
                            Double lat, Double lng, LocalDateTime createdAt, String location, String referrerName, String referrerCode, long referrals) {}
    public record MapPoint(long id, String name, String code, double lat, double lng, String ward, String lga, String state, long members) {}
    public record ReportRow(long id, String category, String title, String body, String status, String adminNote, Double lat, Double lng, LocalDateTime createdAt,
                            String memberCode, String memberName, String phone, String location) {}
    public record Profile(Member m, String zoneName, String stateName, String lgaName, String wardName, String puName, String puCode, Double puLatRef, Double puLngRef,
                          String referrerName, String referrerCode, long directReferrals, long totalDownline) {
        public Double lat() { return m.getPuLatitude() != null ? m.getPuLatitude() : puLatRef; }
        public Double lng() { return m.getPuLongitude() != null ? m.getPuLongitude() : puLngRef; }
        public String locationLine() { return join(", ", puName, wardName, lgaName, stateName); }
    }
    public record ReferralRow(long id, String memberCode, String firstName, String lastName, String phone, LocalDateTime createdAt, String location, long theirReferrals) {}
    public record Referrals(List<ReferralRow> direct, List<long[]> levels, long totalDownline) {}
    public record Coverage(long pollingUnit, long ward, long lga, long state) {}
    public record LocationStats(long states, long lgas, long wards, long pollingUnits, long pusWithCoords, List<Object[]> byState) {}

    /** WHERE fragment on alias m plus its parameters. */
    private record Where(String sql, List<Object> params) {}

    private Where where(Member user, AdminFilter f) {
        StringBuilder sb = new StringBuilder("1=1");
        List<Object> p = new ArrayList<>();
        Role role = user.getRole();
        boolean stateLocked = role == Role.COORDINATOR || role == Role.LGA_COORDINATOR || role == Role.WARD_COORDINATOR || role == Role.POLLING_UNIT_COORDINATOR;
        boolean lgaLocked = role == Role.LGA_COORDINATOR || role == Role.WARD_COORDINATOR || role == Role.POLLING_UNIT_COORDINATOR;
        boolean wardLocked = role == Role.WARD_COORDINATOR || role == Role.POLLING_UNIT_COORDINATOR;
        boolean puLocked = role == Role.POLLING_UNIT_COORDINATOR;
        Long zone = (role == Role.GRAND_PATRON || role == Role.ZONAL_COORDINATOR) ? user.getZoneId() : f.getZoneId();
        Long state = stateLocked ? user.getStateId() : f.getStateId();
        Long lga = lgaLocked ? user.getLgaId() : f.getLgaId();
        Long ward = wardLocked ? user.getWardId() : f.getWardId();
        Long pu = puLocked ? user.getPollingUnitId() : f.getPollingUnitId();
        if (zone != null) { sb.append(" AND m.zone_id = ?"); p.add(zone); }
        if (state != null) { sb.append(" AND m.state_id = ?"); p.add(state); }
        if (lga != null) { sb.append(" AND m.lga_id = ?"); p.add(lga); }
        if (ward != null) { sb.append(" AND m.ward_id = ?"); p.add(ward); }
        if (pu != null) { sb.append(" AND m.polling_unit_id = ?"); p.add(pu); }
        if ("Male".equals(f.getGender()) || "Female".equals(f.getGender())) { sb.append(" AND m.gender = ?"); p.add(f.getGender()); }
        String q = f.getQ() == null ? "" : f.getQ().trim();
        if (!q.isEmpty()) {
            String like = "%" + q.toLowerCase(Locale.ROOT).substring(0, Math.min(60, q.length())) + "%";
            sb.append(" AND (LOWER(m.first_name) LIKE ? OR LOWER(m.last_name) LIKE ? OR LOWER(m.phone) LIKE ? OR LOWER(m.member_code) LIKE ? OR LOWER(m.referral_code) LIKE ?)");
            for (int i = 0; i < 5; i++) p.add(like);
        }
        return new Where(sb.toString(), p);
    }

    private long count(String sql, List<Object> params) { Long n = jdbc.queryForObject(sql, Long.class, params.toArray()); return n == null ? 0 : n; }
    private static List<Object> plus(List<Object> a, Object... more) { List<Object> l = new ArrayList<>(a); l.addAll(Arrays.asList(more)); return l; }
    private static Timestamp ts(LocalDateTime t) { return Timestamp.valueOf(t); }
    private static String join(String sep, String... parts) { StringJoiner j = new StringJoiner(sep); for (String s : parts) if (s != null && !s.isBlank()) j.add(s); return j.toString(); }

    public Summary summary(Member user, AdminFilter f) {
        Where w = where(user, f);
        String base = "SELECT COUNT(*) FROM members m WHERE " + w.sql;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long members = count(base, w.params);
        long today = count(base + " AND m.created_at >= ?", plus(w.params, ts(now.toLocalDate().atStartOfDay())));
        long week = count(base + " AND m.created_at >= ?", plus(w.params, ts(now.minusDays(7))));
        long male = count(base + " AND m.gender = 'Male'", w.params), female = count(base + " AND m.gender = 'Female'", w.params);
        long referred = count(base + " AND m.referred_by IS NOT NULL", w.params), withCoords = count(base + " AND m.pu_latitude IS NOT NULL", w.params);
        long pus = count("SELECT COUNT(DISTINCT m.polling_unit_id) FROM members m WHERE " + w.sql, w.params);
        long wards = count("SELECT COUNT(DISTINCT m.ward_id) FROM members m WHERE " + w.sql, w.params);
        long lgas = count("SELECT COUNT(DISTINCT m.lga_id) FROM members m WHERE " + w.sql, w.params);
        long statesN = count("SELECT COUNT(DISTINCT m.state_id) FROM members m WHERE " + w.sql, w.params);
        long open = count("SELECT COUNT(*) FROM field_reports r JOIN members m ON m.id = r.member_id WHERE " + w.sql + " AND r.status = 'OPEN'", w.params);

        List<NameCount> byZone = jdbc.query("SELECT z.name, COUNT(m.id) FROM zones z LEFT JOIN members m ON m.zone_id = z.id AND " + w.sql + " GROUP BY z.id, z.name ORDER BY 2 DESC, z.name", (rs, i) -> new NameCount(rs.getString(1), rs.getLong(2)), w.params.toArray());
        List<NameCount> byState = jdbc.query("SELECT s.name, COUNT(m.id) FROM states s LEFT JOIN members m ON m.state_id = s.id AND " + w.sql + " GROUP BY s.id, s.name ORDER BY 2 DESC, s.name", (rs, i) -> new NameCount(rs.getString(1), rs.getLong(2)), w.params.toArray());
        List<NameCount> topLgas = jdbc.query("SELECT l.name || ', ' || s.name, COUNT(*) FROM members m JOIN lgas l ON l.id = m.lga_id JOIN states s ON s.id = l.state_id WHERE " + w.sql + " GROUP BY l.id, l.name, s.name ORDER BY 2 DESC LIMIT 10", (rs, i) -> new NameCount(rs.getString(1), rs.getLong(2)), w.params.toArray());
        List<NameCount> topWards = jdbc.query("SELECT w.name || ' (' || l.name || ')', COUNT(*) FROM members m JOIN wards w ON w.id = m.ward_id JOIN lgas l ON l.id = w.lga_id WHERE " + w.sql + " GROUP BY w.id, w.name, l.name ORDER BY 2 DESC LIMIT 10", (rs, i) -> new NameCount(rs.getString(1), rs.getLong(2)), w.params.toArray());
        List<NameCount> topPus = jdbc.query("SELECT COALESCE(p.code || ' ', '') || p.name || ' (' || w.name || ')', COUNT(*) FROM members m JOIN polling_units p ON p.id = m.polling_unit_id JOIN wards w ON w.id = p.ward_id WHERE " + w.sql + " GROUP BY p.id, p.code, p.name, w.name ORDER BY 2 DESC LIMIT 10", (rs, i) -> new NameCount(rs.getString(1), rs.getLong(2)), w.params.toArray());
        List<Daily> daily = jdbc.query("SELECT CAST(m.created_at AS DATE) AS d, COUNT(*) FROM members m WHERE " + w.sql + " AND m.created_at >= ? GROUP BY CAST(m.created_at AS DATE) ORDER BY d", (rs, i) -> new Daily(rs.getDate(1).toLocalDate(), rs.getLong(2)), plus(w.params, ts(now.minusDays(30))).toArray());
        List<Referrer> topReferrers = jdbc.query("SELECT r.id, r.member_code, r.first_name, r.last_name, s.name, COUNT(m.id) FROM members m JOIN members r ON r.id = m.referred_by LEFT JOIN states s ON s.id = r.state_id WHERE " + w.sql + " GROUP BY r.id, r.member_code, r.first_name, r.last_name, s.name ORDER BY 6 DESC LIMIT 10",
            (rs, i) -> new Referrer(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6)), w.params.toArray());
        Map<String, Long> bands = new LinkedHashMap<>();
        for (String b : List.of("Under 18", "18-25", "26-35", "36-45", "46-60", "60+", "Unknown")) bands.put(b, 0L);
        jdbc.query("SELECT m.dob FROM members m WHERE " + w.sql, rs -> {
            java.sql.Date d = rs.getDate(1);
            String band = "Unknown";
            if (d != null) { int age = Period.between(d.toLocalDate(), LocalDate.now()).getYears(); band = age < 18 ? "Under 18" : age < 26 ? "18-25" : age < 36 ? "26-35" : age < 46 ? "36-45" : age < 61 ? "46-60" : "60+"; }
            bands.merge(band, 1L, Long::sum);
        }, w.params.toArray());
        List<NameCount> ageBands = bands.entrySet().stream().filter(e -> e.getValue() > 0).map(e -> new NameCount(e.getKey(), e.getValue())).toList();
        return new Summary(members, today, week, male, female, referred, withCoords, pus, wards, lgas, statesN, open, byZone, byState, topLgas, topWards, topPus, daily, topReferrers, ageBands);
    }

    public List<AreaRow> breakdown(Member user, AdminFilter f, String level) {
        Where w = where(user, f);
        String table, col, code;
        switch (level == null ? "state" : level) {
            case "zone" -> { table = "zones"; col = "m.zone_id"; code = "NULL"; }
            case "lga" -> { table = "lgas"; col = "m.lga_id"; code = "t.code"; }
            case "ward" -> { table = "wards"; col = "m.ward_id"; code = "t.code"; }
            case "pu" -> { table = "polling_units"; col = "m.polling_unit_id"; code = "t.code"; }
            default -> { table = "states"; col = "m.state_id"; code = "t.code"; }
        }
        return jdbc.query("SELECT t.id, t.name, " + code + " AS code, COUNT(m.id), SUM(CASE WHEN m.gender = 'Male' THEN 1 ELSE 0 END), SUM(CASE WHEN m.gender = 'Female' THEN 1 ELSE 0 END) FROM members m JOIN " + table + " t ON t.id = " + col + " WHERE " + w.sql + " GROUP BY t.id, t.name, " + code + " ORDER BY 4 DESC, t.name",
            (rs, i) -> new AreaRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5), rs.getLong(6)), w.params.toArray());
    }

    public long countMembers(Member user, AdminFilter f) { Where w = where(user, f); return count("SELECT COUNT(*) FROM members m WHERE " + w.sql, w.params); }

    public List<MemberRow> listMembers(Member user, AdminFilter f, int page, int size) {
        Where w = where(user, f);
        return jdbc.query("SELECT m.id, m.member_code, m.role, m.status, m.first_name, m.last_name, m.gender, m.phone, m.pu_latitude, m.pu_longitude, m.created_at, " +
                "s.name, l.name, w.name, p.name, r.member_code, r.first_name, r.last_name, (SELECT COUNT(*) FROM members x WHERE x.referred_by = m.id) " +
                "FROM members m LEFT JOIN states s ON s.id = m.state_id LEFT JOIN lgas l ON l.id = m.lga_id LEFT JOIN wards w ON w.id = m.ward_id LEFT JOIN polling_units p ON p.id = m.polling_unit_id LEFT JOIN members r ON r.id = m.referred_by " +
                "WHERE " + w.sql + " ORDER BY m.created_at DESC LIMIT ? OFFSET ?",
            (rs, i) -> new MemberRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                (Double) rs.getObject(9), (Double) rs.getObject(10), rs.getTimestamp(11).toLocalDateTime(), join(", ", rs.getString(15), rs.getString(14), rs.getString(13), rs.getString(12)),
                rs.getString(17) == null ? null : rs.getString(17) + " " + rs.getString(18), rs.getString(16), rs.getLong(19)),
            plus(w.params, size, (page - 1) * size).toArray());
    }

    public List<Map<String, Object>> exportRows(Member user, AdminFilter f) {
        Where w = where(user, f);
        return jdbc.queryForList("SELECT m.member_code, m.first_name, m.last_name, m.other_name, m.gender, m.dob, m.phone, m.email, m.address, m.occupation, m.education, m.vin, " +
            "z.name AS zone, s.name AS state, l.name AS lga, w.name AS ward, p.code AS pu_code, p.name AS polling_unit, m.pu_latitude, m.pu_longitude, m.referral_code, r.member_code AS referred_by, m.role, m.status, m.created_at " +
            "FROM members m LEFT JOIN zones z ON z.id = m.zone_id LEFT JOIN states s ON s.id = m.state_id LEFT JOIN lgas l ON l.id = m.lga_id LEFT JOIN wards w ON w.id = m.ward_id LEFT JOIN polling_units p ON p.id = m.polling_unit_id LEFT JOIN members r ON r.id = m.referred_by " +
            "WHERE " + w.sql + " ORDER BY m.created_at", w.params.toArray());
    }

    public List<MapPoint> mapPoints(Member user, AdminFilter f) {
        Where w = where(user, f);
        return jdbc.query("SELECT p.id, p.name, p.code, COALESCE(p.latitude, AVG(m.pu_latitude)) AS lat, COALESCE(p.longitude, AVG(m.pu_longitude)) AS lng, w.name, l.name, s.name, COUNT(m.id) " +
                "FROM members m JOIN polling_units p ON p.id = m.polling_unit_id JOIN wards w ON w.id = p.ward_id JOIN lgas l ON l.id = w.lga_id JOIN states s ON s.id = l.state_id " +
                "WHERE " + w.sql + " GROUP BY p.id, p.name, p.code, p.latitude, p.longitude, w.name, l.name, s.name HAVING COALESCE(p.latitude, AVG(m.pu_latitude)) IS NOT NULL AND COALESCE(p.longitude, AVG(m.pu_longitude)) IS NOT NULL",
            (rs, i) -> new MapPoint(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getDouble(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getLong(9)), w.params.toArray());
    }

    public List<ReportRow> reports(Member user, AdminFilter f, String status) {
        Where w = where(user, f);
        boolean filter = status != null && List.of("OPEN", "REVIEWED", "CLOSED").contains(status);
        List<Object> params = filter ? plus(w.params, status) : w.params;
        return jdbc.query("SELECT r.id, r.category, r.title, r.body, r.status, r.admin_note, r.latitude, r.longitude, r.created_at, m.member_code, m.first_name || ' ' || m.last_name, m.phone, s.name, l.name, w.name, p.name " +
                "FROM field_reports r JOIN members m ON m.id = r.member_id LEFT JOIN states s ON s.id = r.state_id LEFT JOIN lgas l ON l.id = r.lga_id LEFT JOIN wards w ON w.id = r.ward_id LEFT JOIN polling_units p ON p.id = r.polling_unit_id " +
                "WHERE " + w.sql + (filter ? " AND r.status = ?" : "") + " ORDER BY r.created_at DESC LIMIT 500",
            (rs, i) -> new ReportRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), (Double) rs.getObject(7), (Double) rs.getObject(8), rs.getTimestamp(9).toLocalDateTime(),
                rs.getString(10), rs.getString(11), rs.getString(12), join(", ", rs.getString(16), rs.getString(15), rs.getString(14), rs.getString(13))), params.toArray());
    }

    public Profile profile(Member m) {
        Map<String, Object> row = jdbc.queryForMap("SELECT z.name AS zone_name, s.name AS state_name, l.name AS lga_name, w.name AS ward_name, p.name AS pu_name, p.code AS pu_code, p.latitude AS pu_lat, p.longitude AS pu_lng, " +
            "r.first_name AS rf, r.last_name AS rl, r.member_code AS rc FROM members m LEFT JOIN zones z ON z.id = m.zone_id LEFT JOIN states s ON s.id = m.state_id LEFT JOIN lgas l ON l.id = m.lga_id " +
            "LEFT JOIN wards w ON w.id = m.ward_id LEFT JOIN polling_units p ON p.id = m.polling_unit_id LEFT JOIN members r ON r.id = m.referred_by WHERE m.id = ?", m.getId());
        long direct = count("SELECT COUNT(*) FROM members WHERE referred_by = ?", List.of(m.getId()));
        long total = count("WITH RECURSIVE tree(id) AS (SELECT id FROM members WHERE referred_by = ? UNION ALL SELECT c.id FROM members c JOIN tree t ON c.referred_by = t.id) SELECT COUNT(*) FROM tree", List.of(m.getId()));
        String rf = (String) row.get("rf");
        return new Profile(m, (String) row.get("zone_name"), (String) row.get("state_name"), (String) row.get("lga_name"), (String) row.get("ward_name"), (String) row.get("pu_name"), (String) row.get("pu_code"),
            (Double) row.get("pu_lat"), (Double) row.get("pu_lng"), rf == null ? null : rf + " " + row.get("rl"), (String) row.get("rc"), direct, total);
    }

    public Referrals referrals(long memberId) {
        List<ReferralRow> direct = jdbc.query("SELECT m.id, m.member_code, m.first_name, m.last_name, m.phone, m.created_at, s.name, l.name, w.name, p.name, (SELECT COUNT(*) FROM members x WHERE x.referred_by = m.id) " +
                "FROM members m LEFT JOIN states s ON s.id = m.state_id LEFT JOIN lgas l ON l.id = m.lga_id LEFT JOIN wards w ON w.id = m.ward_id LEFT JOIN polling_units p ON p.id = m.polling_unit_id WHERE m.referred_by = ? ORDER BY m.created_at DESC",
            (rs, i) -> new ReferralRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toLocalDateTime(), join(", ", rs.getString(10), rs.getString(9), rs.getString(8)), rs.getLong(11)), memberId);
        List<long[]> levels = jdbc.query("WITH RECURSIVE tree(id, lvl) AS (SELECT id, 1 FROM members WHERE referred_by = ? UNION ALL SELECT m.id, t.lvl + 1 FROM members m JOIN tree t ON m.referred_by = t.id WHERE t.lvl < 10) SELECT lvl, COUNT(*) FROM tree GROUP BY lvl ORDER BY lvl",
            (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, memberId);
        return new Referrals(direct, levels, levels.stream().mapToLong(l -> l[1]).sum());
    }

    public Coverage coverage(Member m) {
        return new Coverage(m.getPollingUnitId() == null ? 0 : count("SELECT COUNT(*) FROM members WHERE polling_unit_id = ?", List.of(m.getPollingUnitId())),
            m.getWardId() == null ? 0 : count("SELECT COUNT(*) FROM members WHERE ward_id = ?", List.of(m.getWardId())),
            m.getLgaId() == null ? 0 : count("SELECT COUNT(*) FROM members WHERE lga_id = ?", List.of(m.getLgaId())),
            m.getStateId() == null ? 0 : count("SELECT COUNT(*) FROM members WHERE state_id = ?", List.of(m.getStateId())));
    }

    public LocationStats locationStats() {
        List<Object[]> byState = jdbc.query("SELECT s.name, (SELECT COUNT(*) FROM wards w JOIN lgas l ON l.id = w.lga_id WHERE l.state_id = s.id), (SELECT COUNT(*) FROM polling_units p JOIN wards w ON w.id = p.ward_id JOIN lgas l ON l.id = w.lga_id WHERE l.state_id = s.id) FROM states s ORDER BY s.name",
            (rs, i) -> new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3)});
        return new LocationStats(count("SELECT COUNT(*) FROM states", List.of()), count("SELECT COUNT(*) FROM lgas", List.of()), count("SELECT COUNT(*) FROM wards", List.of()),
            count("SELECT COUNT(*) FROM polling_units", List.of()), count("SELECT COUNT(*) FROM polling_units WHERE latitude IS NOT NULL", List.of()), byState);
    }

    public long openReportsFor(Member user) { return count("SELECT COUNT(*) FROM field_reports r JOIN members m ON m.id = r.member_id WHERE " + where(user, new AdminFilter()).sql + " AND r.status = 'OPEN'", where(user, new AdminFilter()).params); }

    /** Public-facing member count on the home page starts at a 7,000,000 baseline and counts up from every real registration. */
    private static final long MEMBER_COUNT_BASELINE = 7_000_000L;
    public long totalMembersDisplay() { return MEMBER_COUNT_BASELINE + count("SELECT COUNT(*) FROM members", List.of()); }
}
