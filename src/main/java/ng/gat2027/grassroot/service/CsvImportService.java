package ng.gat2027.grassroot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.*;

/**
 * Imports wards and polling units (with optional coordinates) from CSV. Flexible headers, e.g.
 * "name, ward_name, local_government_name, state_name, location.latitude, location.longitude" or
 * "state, lga, ward, polling_unit, pu_code, latitude, longitude". Rows are matched by name so re-imports update.
 */
@Service
public class CsvImportService {
    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);
    private final JdbcTemplate jdbc;

    public CsvImportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static class Summary {
        public int rows, wardsCreated, pusCreated, pusUpdated, badCoords;
        public final List<String> lgasCreated = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();
        public String text() {
            return String.format(Locale.UK, "Processed %,d rows: %,d wards created, %,d polling units created, %,d updated, %,d bad coordinates dropped, %,d rows skipped.%s",
                rows, wardsCreated, pusCreated, pusUpdated, badCoords, skipped.size(), lgasCreated.isEmpty() ? "" : " New LGAs: " + String.join("; ", lgasCreated));
        }
    }

    private static final Map<String, List<String>> HEADER_ALIASES = Map.of(
        "state", List.of("state", "statename"),
        "lga", List.of("lga", "lganame", "localgovernment", "localgovernmentarea", "localgovernmentname", "lgname"),
        "ward", List.of("ward", "wardname", "ra", "registrationarea", "raname"),
        "ward_code", List.of("wardcode", "wardno", "wardnumber", "racode"),
        "pu", List.of("pu", "puname", "pollingunit", "pollingunitname", "pullingunit", "pullingunitname", "unit", "unitname", "name"),
        "pu_code", List.of("pucode", "pollingunitcode", "code", "puno", "punumber", "delimitation", "delimitationcode"),
        "latitude", List.of("latitude", "lat", "y", "locationlatitude", "gpslatitude"),
        "longitude", List.of("longitude", "lng", "lon", "long", "x", "locationlongitude", "gpslongitude"));
    private static final Map<String, String> STATE_ALIASES = Map.of("fct", "federalcapitalterritory", "abuja", "federalcapitalterritory", "fctabuja", "federalcapitalterritory", "abujafct", "federalcapitalterritory", "nassarawa", "nasarawa");
    private static final Map<String, String> LGA_ALIASES = new HashMap<>();
    static {
        String[][] a = {{"kogikk", "kogi"}, {"kogikotonkarfe", "kogi"}, {"kotonkarfe", "kogi"}, {"egbadosouth", "yewasouth"}, {"egbadonorth", "yewanorth"}, {"birninmagaji", "birninmagajikiyaw"},
            {"sbirni", "sabonbirni"}, {"aliero", "aleiro"}, {"arewa", "arewadandi"}, {"municipal", "abujamunicipal"}, {"amac", "abujamunicipal"}, {"abujamunicipalareacouncil", "abujamunicipal"},
            {"danbata", "dambatta"}, {"ezinihittembaise", "ezinihitte"}, {"otukpo", "oturkpo"}, {"somolu", "shomolu"}, {"ogbomosonorth", "ogbomoshonorth"}, {"ogbomososouth", "ogbomoshosouth"},
            {"ileshaeast", "ilesaeast"}, {"ileshawest", "ilesawest"}, {"kwaanpan", "quaanpan"}, {"nasarawaeggon", "nasarawaegon"}, {"wassagudanko", "wasagudanko"}, {"mainlandlagos", "lagosmainland"}, {"islandlagos", "lagosisland"}};
        for (String[] p : a) LGA_ALIASES.put(p[0], p[1]);
    }

    static String norm(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replace("&", "and").replaceAll("[^a-z0-9]", ""); }
    static String lgaNorm(String s) { String n = norm(s == null ? "" : s.replaceAll("\\(.*?\\)", "").replaceAll("(?i)\\blga\\b", "")); return LGA_ALIASES.getOrDefault(n, n); }
    static String tidy(String s) { return s == null ? "" : s.replaceAll("\\s+", " ").trim(); }
    static boolean inNigeria(Double lat, Double lng) { return lat != null && lng != null && lat >= 4 && lat <= 14.2 && lng >= 2.5 && lng <= 15; }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) cur[j] = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    public static List<String[]> parseCsv(String text) {
        List<String[]> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        if (text.startsWith("﻿")) text = text.substring(1);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') { if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; } else inQuotes = false; }
                else field.append(c);
            } else if (c == '"') inQuotes = true;
            else if (c == ',') { row.add(field.toString()); field.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(row.toArray(new String[0]));
                row = new ArrayList<>();
            } else field.append(c);
        }
        row.add(field.toString());
        if (row.stream().anyMatch(v -> !v.isBlank())) rows.add(row.toArray(new String[0]));
        return rows;
    }

    private record LgaRef(long id, String n, String name) {}
    private record WardKey(long lgaId, String name) {}
    private record PuRow(WardKey ward, String name, String code, Double lat, Double lng) {}

    @Transactional
    public Summary importCsv(String csvText) {
        List<String[]> rows = parseCsv(csvText);
        if (rows.size() < 2) throw new IllegalArgumentException("CSV has no data rows");
        Map<String, Integer> idx = new HashMap<>();
        String[] header = rows.get(0);
        for (int i = 0; i < header.length; i++) {
            String key = norm(header[i]);
            for (var e : HEADER_ALIASES.entrySet()) if (!idx.containsKey(e.getKey()) && e.getValue().contains(key)) idx.put(e.getKey(), i);
        }
        for (String need : List.of("state", "lga", "ward")) if (!idx.containsKey(need)) throw new IllegalArgumentException("CSV is missing a \"" + need + "\" column. Found headers: " + String.join(", ", header));
        boolean hasPu = idx.containsKey("pu");
        Summary s = new Summary(); s.rows = rows.size() - 1;

        Map<String, Long> stateByNorm = new HashMap<>();
        jdbc.query("SELECT id, name FROM states", rs -> { stateByNorm.put(norm(rs.getString(2)), rs.getLong(1)); });
        Map<Long, List<LgaRef>> lgasByState = new HashMap<>();
        jdbc.query("SELECT id, state_id, name FROM lgas", rs -> { lgasByState.computeIfAbsent(rs.getLong(2), k -> new ArrayList<>()).add(new LgaRef(rs.getLong(1), lgaNorm(rs.getString(3)), rs.getString(3))); });

        Map<String, Long> lgaCache = new HashMap<>();
        LinkedHashMap<WardKey, String[]> wardMap = new LinkedHashMap<>(); // key -> [display name, code]
        List<PuRow> puList = new ArrayList<>();

        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            String stateName = cell(row, idx.get("state")), lgaName = cell(row, idx.get("lga")), wardName = cell(row, idx.get("ward"));
            if (stateName == null || lgaName == null || wardName == null) { s.skipped.add("row " + (r + 1) + ": missing state/lga/ward"); continue; }
            String sn = norm(stateName).replaceAll("state$", ""); sn = STATE_ALIASES.getOrDefault(sn, sn);
            Long stateId = stateByNorm.get(sn);
            if (stateId == null) { s.skipped.add("row " + (r + 1) + ": unknown state \"" + stateName + "\""); continue; }
            String ln = lgaNorm(lgaName), lgaKey = stateId + "|" + ln;
            Long lgaId = lgaCache.get(lgaKey);
            if (lgaId == null) {
                List<LgaRef> list = lgasByState.computeIfAbsent(stateId, k -> new ArrayList<>());
                LgaRef hit = list.stream().filter(l -> l.n.equals(ln)).findFirst().orElse(null);
                if (hit == null && ln.length() >= 5) hit = list.stream().filter(l -> l.n.startsWith(ln) || ln.startsWith(l.n)).findFirst().orElse(null);
                if (hit == null) {
                    LgaRef best = null; int bestD = Integer.MAX_VALUE;
                    for (LgaRef l : list) { int d = levenshtein(l.n, ln); if (d < bestD) { bestD = d; best = l; } }
                    if (best != null && bestD <= Math.max(1, ln.length() / 5)) hit = best;
                }
                if (hit == null) {
                    String code = String.format("%02d", list.size() + 1);
                    jdbc.update("INSERT INTO lgas (state_id, code, name) VALUES (?, ?, ?)", stateId, code, lgaName);
                    Long id = jdbc.queryForObject("SELECT id FROM lgas WHERE state_id = ? AND name = ?", Long.class, stateId, lgaName);
                    hit = new LgaRef(id, ln, lgaName); list.add(hit);
                    s.lgasCreated.add(lgaName + " (" + stateName + ")");
                }
                lgaId = hit.id; lgaCache.put(lgaKey, lgaId);
            }
            WardKey wk = new WardKey(lgaId, wardName.toLowerCase(Locale.ROOT));
            wardMap.putIfAbsent(wk, new String[]{wardName, idx.containsKey("ward_code") ? cell(row, idx.get("ward_code")) : null});
            if (!hasPu) continue;
            String puName = cell(row, idx.get("pu"));
            if (puName == null) continue;
            Double lat = idx.containsKey("latitude") ? num(cell(row, idx.get("latitude"))) : null, lng = idx.containsKey("longitude") ? num(cell(row, idx.get("longitude"))) : null;
            if ((lat != null || lng != null) && !inNigeria(lat, lng)) { lat = null; lng = null; s.badCoords++; }
            puList.add(new PuRow(wk, puName, idx.containsKey("pu_code") ? cell(row, idx.get("pu_code")) : null, lat, lng));
        }

        // Wards: insert the missing ones in batches, then map names -> ids.
        Map<WardKey, Long> wardId = new HashMap<>();
        jdbc.query("SELECT id, lga_id, name FROM wards", rs -> { wardId.put(new WardKey(rs.getLong(2), rs.getString(3).toLowerCase(Locale.ROOT)), rs.getLong(1)); });
        List<Object[]> newWards = new ArrayList<>();
        for (var e : wardMap.entrySet()) if (!wardId.containsKey(e.getKey())) newWards.add(new Object[]{e.getKey().lgaId, e.getValue()[1], e.getValue()[0]});
        for (int i = 0; i < newWards.size(); i += 1000) {
            jdbc.batchUpdate("INSERT INTO wards (lga_id, code, name) VALUES (?, ?, ?)", newWards.subList(i, Math.min(i + 1000, newWards.size())), 1000,
                (ps, a) -> { ps.setLong(1, (Long) a[0]); ps.setObject(2, a[1], Types.VARCHAR); ps.setString(3, (String) a[2]); });
        }
        s.wardsCreated = newWards.size();
        if (!newWards.isEmpty()) jdbc.query("SELECT id, lga_id, name FROM wards", rs -> { wardId.put(new WardKey(rs.getLong(2), rs.getString(3).toLowerCase(Locale.ROOT)), rs.getLong(1)); });

        // Polling units: existing ones get code / coordinates filled in, new ones are inserted.
        Map<String, long[]> existing = new HashMap<>(); // wardId|lower name -> [id, hasCoords]
        jdbc.query("SELECT id, ward_id, name, latitude FROM polling_units", rs -> { existing.put(rs.getLong(2) + "|" + rs.getString(3).toLowerCase(Locale.ROOT), new long[]{rs.getLong(1), rs.getObject(4) == null ? 0 : 1}); });
        Set<String> seen = new HashSet<>();
        List<Object[]> inserts = new ArrayList<>(), updates = new ArrayList<>();
        for (PuRow p : puList) {
            Long wid = wardId.get(p.ward); if (wid == null) continue;
            String key = wid + "|" + p.name.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) continue;
            long[] ex = existing.get(key);
            if (ex == null) inserts.add(new Object[]{wid, p.code, p.name, p.lat, p.lng});
            else if (p.code != null || (p.lat != null && p.lng != null)) updates.add(new Object[]{p.code, p.lat, p.lng, ex[0]});
        }
        for (int i = 0; i < inserts.size(); i += 2000) {
            jdbc.batchUpdate("INSERT INTO polling_units (ward_id, code, name, latitude, longitude) VALUES (?, ?, ?, ?, ?)", inserts.subList(i, Math.min(i + 2000, inserts.size())), 2000,
                (ps, a) -> { ps.setLong(1, (Long) a[0]); ps.setObject(2, a[1], Types.VARCHAR); ps.setString(3, (String) a[2]); ps.setObject(4, a[3], Types.DOUBLE); ps.setObject(5, a[4], Types.DOUBLE); });
            if ((i / 2000) % 20 == 0) log.info("[import] {} / {} polling units", Math.min(i + 2000, inserts.size()), inserts.size());
        }
        for (int i = 0; i < updates.size(); i += 2000) {
            jdbc.batchUpdate("UPDATE polling_units SET code = COALESCE(?, code), latitude = COALESCE(?, latitude), longitude = COALESCE(?, longitude) WHERE id = ?", updates.subList(i, Math.min(i + 2000, updates.size())), 2000,
                (ps, a) -> { ps.setObject(1, a[0], Types.VARCHAR); ps.setObject(2, a[1], Types.DOUBLE); ps.setObject(3, a[2], Types.DOUBLE); ps.setLong(4, (Long) a[3]); });
        }
        s.pusCreated = inserts.size(); s.pusUpdated = updates.size();
        log.info("[import] {}", s.text());
        return s;
    }

    private static String cell(String[] row, Integer i) { if (i == null || i >= row.length) return null; String v = tidy(row[i]); return v.isEmpty() ? null : v; }
    private static Double num(String v) { if (v == null) return null; try { double d = Double.parseDouble(v.trim()); return Double.isFinite(d) ? d : null; } catch (NumberFormatException e) { return null; } }
}
