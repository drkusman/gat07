package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.AppSetting;
import ng.gat2027.grassroot.repo.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.stream.Collectors;

/** Admin-managed application settings: registration age limits. */
@Service
public class SettingsService {
    public record AgeRules(int minAge, Integer maxAge, boolean requireDob) {
        public String maxDob() { return LocalDate.now().minusYears(minAge).toString(); }
    }

    public static final AgeRules DEFAULTS = new AgeRules(16, null, true);
    private final AppSettingRepository repo;

    public SettingsService(AppSettingRepository repo) { this.repo = repo; }

    public AgeRules ageRules() {
        Map<String, String> m = repo.findAll().stream().collect(Collectors.toMap(AppSetting::getKey, AppSetting::getValue));
        Integer min = parse(m.get("min_age")), max = parse(m.get("max_age"));
        boolean requireDob = m.containsKey("require_dob") ? Boolean.parseBoolean(m.get("require_dob")) : DEFAULTS.requireDob();
        return new AgeRules(min == null ? DEFAULTS.minAge() : min, max, requireDob);
    }

    @Transactional
    public void save(AgeRules r) {
        repo.save(new AppSetting("min_age", String.valueOf(r.minAge())));
        repo.save(new AppSetting("max_age", r.maxAge() == null ? "" : String.valueOf(r.maxAge())));
        repo.save(new AppSetting("require_dob", String.valueOf(r.requireDob())));
    }

    public static Integer ageOf(LocalDate dob) { return dob == null ? null : Period.between(dob, LocalDate.now()).getYears(); }

    /** Error message when the date of birth breaks the rules, otherwise null. */
    public static String ageError(LocalDate dob, AgeRules r) {
        if (dob == null) return r.requireDob() ? "Date of birth is required" : null;
        if (dob.isAfter(LocalDate.now()) || dob.isBefore(LocalDate.now().minusYears(130))) return "Enter a valid date of birth";
        int age = ageOf(dob);
        if (age < r.minAge()) return "You must be at least " + r.minAge() + " years old to register as a GAT member";
        if (r.maxAge() != null && age > r.maxAge()) return "Registration is open to members aged " + r.maxAge() + " and below";
        return null;
    }

    private static Integer parse(String v) {
        if (v == null || v.isBlank()) return null;
        try { int n = Integer.parseInt(v.trim()); return n > 0 ? n : null; } catch (NumberFormatException e) { return null; }
    }
}
