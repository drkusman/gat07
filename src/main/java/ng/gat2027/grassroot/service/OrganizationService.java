package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.Committee;
import ng.gat2027.grassroot.domain.Position;
import ng.gat2027.grassroot.domain.Role;
import ng.gat2027.grassroot.repo.CommitteeRepository;
import ng.gat2027.grassroot.repo.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrganizationService {
    public record PositionRow(Position position, String committeeCode, String committeeName) {}
    public record CommitteeRow(Committee committee, long positionCount) {}
    public record CommitteeGroup(Committee committee, List<Position> positions) {}

    /** Positions whose title exactly matches one of the system's operational roles carry that role; every other
     *  position (committee secretaries, treasurers, auditors, PWD leaders, etc.) leaves the member as a plain Member.
     *  Keyed by the position title, lowercased/trimmed, so a rename that still matches keeps working. */
    private static final Map<String, Role> ROLE_BY_POSITION_TITLE = Map.ofEntries(
        Map.entry("local government/area council coordinator", Role.LGA_COORDINATOR),
        Map.entry("ward coordinator", Role.WARD_COORDINATOR),
        Map.entry("polling unit coordinator", Role.POLLING_UNIT_COORDINATOR),
        Map.entry("state coordinator", Role.COORDINATOR),
        Map.entry("zonal coordinator", Role.ZONAL_COORDINATOR),
        Map.entry("national coordinator", Role.ADMIN),
        Map.entry("national media/publicity secretary", Role.NATIONAL_PUBLICITY_SECRETARY)
    );

    public static Role roleForPositionTitle(String title) {
        return ROLE_BY_POSITION_TITLE.getOrDefault(title == null ? "" : title.trim().toLowerCase(Locale.ROOT), Role.MEMBER);
    }

    public static class ImportSummary {
        public int rows, created, skipped;
        public final List<String> errors = new ArrayList<>();
        public String text() {
            return String.format("Processed %,d rows: %,d positions added, %,d skipped.%s",
                rows, created, skipped, errors.isEmpty() ? "" : " First issues: " + String.join("; ", errors));
        }
    }

    private final CommitteeRepository committees;
    private final PositionRepository positions;

    public OrganizationService(CommitteeRepository committees, PositionRepository positions) {
        this.committees = committees; this.positions = positions;
    }

    // ----- committees -----
    public List<Committee> committeesList() { return committees.findAllByOrderByNameAsc(); }

    public List<CommitteeRow> committees() {
        return committees.findAllByOrderByNameAsc().stream()
            .map(c -> new CommitteeRow(c, positions.countByCommitteeId(c.getId())))
            .toList();
    }

    @Transactional
    public Committee addCommittee(String code, String name) {
        if (committees.existsByCodeIgnoreCase(code)) throw new IllegalArgumentException("A committee with that code already exists");
        Committee c = new Committee();
        c.setCode(code.trim().toUpperCase(Locale.ROOT));
        c.setName(name.trim());
        return committees.save(c);
    }

    @Transactional
    public void updateCommittee(Long id, String code, String name) {
        Committee c = committees.findById(id).orElseThrow(() -> new IllegalArgumentException("Committee not found"));
        committees.findByCodeIgnoreCase(code).filter(other -> !other.getId().equals(id))
            .ifPresent(other -> { throw new IllegalArgumentException("A committee with that code already exists"); });
        c.setCode(code.trim().toUpperCase(Locale.ROOT));
        c.setName(name.trim());
        committees.save(c);
    }

    @Transactional
    public void deleteCommittee(Long id) {
        long inUse = positions.countByCommitteeId(id);
        if (inUse > 0) throw new IllegalArgumentException("Can't remove this committee — " + inUse + " position(s) still belong to it. Remove or reassign them first.");
        committees.deleteById(id);
    }

    public Optional<Position> findPosition(Long id) { return positions.findById(id); }
    public Optional<Committee> findCommittee(Long id) { return committees.findById(id); }

    public List<CommitteeGroup> positionsGroupedByCommittee() {
        Map<Long, List<Position>> byCommittee = positions.findAllByOrderByTitleAsc().stream()
            .collect(Collectors.groupingBy(Position::getCommitteeId));
        return committees.findAllByOrderByNameAsc().stream()
            .map(c -> new CommitteeGroup(c, byCommittee.getOrDefault(c.getId(), List.of())))
            .toList();
    }

    // ----- positions -----
    public List<PositionRow> positions() {
        Map<Long, Committee> byId = committees.findAll().stream().collect(Collectors.toMap(Committee::getId, Function.identity()));
        return positions.findAllByOrderByTitleAsc().stream()
            .map(p -> new PositionRow(p, byId.containsKey(p.getCommitteeId()) ? byId.get(p.getCommitteeId()).getCode() : "?",
                byId.containsKey(p.getCommitteeId()) ? byId.get(p.getCommitteeId()).getName() : "Unknown committee"))
            .toList();
    }

    @Transactional
    public Position addPosition(String title, Long committeeId) {
        if (!committees.existsById(committeeId)) throw new IllegalArgumentException("Choose a valid committee");
        Position p = new Position();
        p.setTitle(title.trim());
        p.setCommitteeId(committeeId);
        return positions.save(p);
    }

    @Transactional
    public void updatePosition(Long id, String title, Long committeeId) {
        if (!committees.existsById(committeeId)) throw new IllegalArgumentException("Choose a valid committee");
        Position p = positions.findById(id).orElseThrow(() -> new IllegalArgumentException("Position not found"));
        p.setTitle(title.trim());
        p.setCommitteeId(committeeId);
        positions.save(p);
    }

    @Transactional
    public void deletePosition(Long id) { positions.deleteById(id); }

    /** CSV columns: position, committee_code (with or without a header row). Rows whose committee_code doesn't
     *  match an existing committee are skipped and listed — add the committee first, then re-import. */
    @Transactional
    public ImportSummary importCsv(String csv) {
        ImportSummary s = new ImportSummary();
        if (csv == null || csv.isBlank()) return s;
        Map<String, Committee> byCode = committees.findAll().stream()
            .collect(Collectors.toMap(c -> c.getCode().toUpperCase(Locale.ROOT), Function.identity()));
        String[] lines = csv.replace("﻿", "").split("\r?\n");
        int start = 0;
        if (lines.length > 0 && lines[0].toLowerCase(Locale.ROOT).contains("position")) start = 1;
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.equals(",")) continue;
            s.rows++;
            String[] parts = splitCsvLine(line);
            if (parts.length < 2) { skip(s, i + 1, "expected 2 columns (position, committee_code)"); continue; }
            String title = parts[0].trim();
            String code = parts[1].trim().toUpperCase(Locale.ROOT);
            if (title.isBlank()) { skip(s, i + 1, "missing position"); continue; }
            Committee c = byCode.get(code);
            if (c == null) { skip(s, i + 1, "unrecognised committee code '" + parts[1].trim() + "'"); continue; }
            addPosition(title, c.getId());
            s.created++;
        }
        return s;
    }

    private void skip(ImportSummary s, int rowNum, String reason) {
        s.skipped++;
        if (s.errors.size() < 5) s.errors.add("row " + rowNum + ": " + reason);
    }

    private static String[] splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
