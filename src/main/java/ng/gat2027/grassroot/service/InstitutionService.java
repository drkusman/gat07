package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.Institution;
import ng.gat2027.grassroot.repo.InstitutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class InstitutionService {
    public static class ImportSummary {
        public int rows, created, skipped;
        public final List<String> errors = new ArrayList<>();
        public String text() {
            return String.format("Processed %,d rows: %,d institutions added, %,d skipped.%s",
                rows, created, skipped, errors.isEmpty() ? "" : " First issues: " + String.join("; ", errors));
        }
    }

    private final InstitutionRepository repo;

    public InstitutionService(InstitutionRepository repo) { this.repo = repo; }

    public List<Institution> all() { return repo.findAllByOrderByTypeAscOwnershipAscNameAsc(); }
    public List<Institution> find(String type, String ownership) { return repo.findByTypeAndOwnershipOrderByNameAsc(type, ownership); }

    @Transactional
    public Institution add(String name, String type, String ownership) {
        Institution i = new Institution();
        i.setName(name.trim());
        i.setType(type);
        i.setOwnership(ownership);
        return repo.save(i);
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }

    /** CSV columns: name, type, ownership (with or without a header row).
     *  type accepts University/Polytechnic/"College of Education" (or "COE"), case-insensitive.
     *  ownership accepts Federal/State, case-insensitive. Any other value is skipped. */
    @Transactional
    public ImportSummary importCsv(String csv) {
        ImportSummary s = new ImportSummary();
        if (csv == null || csv.isBlank()) return s;
        String[] lines = csv.replace("﻿", "").split("\r?\n");
        int start = 0;
        if (lines.length > 0 && lines[0].toLowerCase(Locale.ROOT).contains("name")) start = 1;
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            s.rows++;
            String[] parts = splitCsvLine(line);
            if (parts.length < 3) { skip(s, i + 1, "expected 3 columns (name, type, ownership)"); continue; }
            String name = parts[0].trim();
            String type = normalizeType(parts[1].trim());
            String ownership = normalizeOwnership(parts[2].trim());
            if (name.isBlank()) { skip(s, i + 1, "missing name"); continue; }
            if (type == null) { skip(s, i + 1, "unrecognised type '" + parts[1].trim() + "'"); continue; }
            if (ownership == null) { skip(s, i + 1, "unrecognised ownership '" + parts[2].trim() + "'"); continue; }
            add(name, type, ownership);
            s.created++;
        }
        return s;
    }

    private void skip(ImportSummary s, int rowNum, String reason) {
        s.skipped++;
        if (s.errors.size() < 5) s.errors.add("row " + rowNum + ": " + reason);
    }

    private static String normalizeType(String raw) {
        String v = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return switch (v) {
            case "university" -> "University";
            case "polytechnic", "poly" -> "Polytechnic";
            case "collegeofeducation", "coe" -> "College of Education";
            default -> null;
        };
    }

    private static String normalizeOwnership(String raw) {
        String v = raw.toLowerCase(Locale.ROOT).trim();
        if (v.equals("federal")) return "Federal";
        if (v.equals("state")) return "State";
        return null;
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
