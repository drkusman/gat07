package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.domain.MemberStatus;
import ng.gat2027.grassroot.domain.Role;
import ng.gat2027.grassroot.repo.MemberRepository;
import ng.gat2027.grassroot.repo.PollingUnitRepository;
import ng.gat2027.grassroot.web.forms.ProfileForm;
import ng.gat2027.grassroot.web.forms.RegisterForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class MemberService {
    public static class MemberException extends RuntimeException { public MemberException(String m) { super(m); } }

    private final MemberRepository members; private final PollingUnitRepository pus; private final LocationService locations;
    private final SettingsService settings; private final PasswordEncoder encoder; private final JdbcTemplate jdbc;

    public MemberService(MemberRepository members, PollingUnitRepository pus, LocationService locations, SettingsService settings, PasswordEncoder encoder, JdbcTemplate jdbc) {
        this.members = members; this.pus = pus; this.locations = locations; this.settings = settings; this.encoder = encoder; this.jdbc = jdbc;
    }

    @Transactional
    public Member register(RegisterForm f) {
        String phone = Codes.normalizePhone(f.getPhone());
        if (!Codes.validPhone(phone)) throw new MemberException("Enter a valid Nigerian phone number (e.g. 0803 123 4567)");
        if (f.getPassword2() != null && !f.getPassword2().equals(f.getPassword())) throw new MemberException("Passwords do not match");
        if (!f.isConsent()) throw new MemberException("Please confirm your details and tick the consent box");
        String ageProblem = SettingsService.ageError(f.getDob(), settings.ageRules());
        if (ageProblem != null) throw new MemberException(ageProblem);
        if (members.existsByPhone(phone)) throw new MemberException("This phone number is already registered. Please log in instead.");

        Long referredBy = null;
        String ref = Codes.blankToNull(f.getReferralCode());
        if (ref != null) {
            Member r = members.findByReferralCodeIgnoreCase(ref).filter(Member::isActive).orElseThrow(() -> new MemberException("Referral code not found. Leave it blank if you have none."));
            referredBy = r.getId();
        }
        var loc = locations.resolve(f, null);

        Member m = new Member();
        m.setMemberCode(Codes.memberCode(members.maxId() + 1));
        m.setReferralCode(uniqueReferralCode());
        m.setReferredBy(referredBy);
        applyBiodata(m, f.getFirstName(), f.getLastName(), f.getOtherName(), f.getGender(), f.getDob(), phone, f.getEmail(), f.getAddress(), f.getOccupation(), f.getEducation(), f.getVin(), f.getPhoto());
        m.setZoneId(loc.zoneId()); m.setStateId(loc.stateId()); m.setLgaId(loc.lgaId()); m.setWardId(loc.wardId()); m.setPollingUnitId(loc.pollingUnitId());
        m.setPuLatitude(loc.lat()); m.setPuLongitude(loc.lng());
        m.setPasswordHash(encoder.encode(f.getPassword()));
        m = members.save(m);
        final Long id = m.getId();
        pus.findById(loc.pollingUnitId()).filter(p -> p.getCreatedBy() == null).ifPresent(p -> { p.setCreatedBy(id); pus.save(p); });
        return m;
    }

    @Transactional
    public void updateProfile(Member m, ProfileForm f) {
        String phone = Codes.normalizePhone(f.getPhone());
        if (!Codes.validPhone(phone)) throw new MemberException("Enter a valid Nigerian phone number (e.g. 0803 123 4567)");
        String ageProblem = SettingsService.ageError(f.getDob(), settings.ageRules());
        if (ageProblem != null) throw new MemberException(ageProblem);
        if (!phone.equals(m.getPhone()) && members.existsByPhone(phone)) throw new MemberException("That phone number is already in use");
        applyBiodata(m, f.getFirstName(), f.getLastName(), f.getOtherName(), f.getGender(), f.getDob(), phone, f.getEmail(), f.getAddress(), f.getOccupation(), f.getEducation(), f.getVin(), f.getPhoto());
        if (f.getZoneId() != null) {
            var loc = locations.resolve(f, m.getId());
            m.setZoneId(loc.zoneId()); m.setStateId(loc.stateId()); m.setLgaId(loc.lgaId()); m.setWardId(loc.wardId()); m.setPollingUnitId(loc.pollingUnitId());
            m.setPuLatitude(loc.lat()); m.setPuLongitude(loc.lng());
        }
        m.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        members.save(m);
    }

    private void applyBiodata(Member m, String first, String last, String other, String gender, java.time.LocalDate dob, String phone, String email, String address, String occupation, String education, String vin, String photo) {
        m.setFirstName(first.trim()); m.setLastName(last.trim()); m.setOtherName(Codes.blankToNull(other)); m.setGender(Codes.blankToNull(gender)); m.setDob(dob); m.setPhone(phone);
        m.setEmail(Codes.blankToNull(email)); m.setAddress(Codes.blankToNull(address)); m.setOccupation(Codes.blankToNull(occupation)); m.setEducation(Codes.blankToNull(education)); m.setVin(Codes.blankToNull(vin));
        if (photo != null && photo.startsWith("data:image/") && photo.length() < 700_000) m.setPhoto(photo);
    }

    @Transactional
    public void changePassword(Member m, String current, String next) {
        if (!encoder.matches(current == null ? "" : current, m.getPasswordHash())) throw new MemberException("Current password is incorrect");
        if (next == null || next.length() < 6) throw new MemberException("New password must be at least 6 characters");
        m.setPasswordHash(encoder.encode(next)); members.save(m);
    }

    // ----- admin operations -----
    @Transactional
    public void setRole(Member admin, Long memberId, Role role) {
        if (admin.getId().equals(memberId) && role != Role.ADMIN) throw new MemberException("You cannot remove your own admin role");
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        m.setRole(role); members.save(m);
    }

    @Transactional
    public void setStatus(Member admin, Long memberId, MemberStatus status) {
        if (admin.getId().equals(memberId)) throw new MemberException("You cannot suspend your own account");
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        m.setStatus(status); members.save(m);
        if (status == MemberStatus.SUSPENDED) jdbc.update("DELETE FROM persistent_logins WHERE username = ?", m.getPhone());
    }

    @Transactional
    public void resetPassword(Long memberId, String password) {
        if (password == null || password.length() < 6) throw new MemberException("Password must be at least 6 characters");
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        m.setPasswordHash(encoder.encode(password)); members.save(m);
    }

    private String uniqueReferralCode() {
        for (int i = 0; i < 20; i++) { String c = Codes.make("GAT", 6); if (!members.existsByReferralCode(c)) return c; }
        throw new IllegalStateException("Could not generate a referral code");
    }
}
