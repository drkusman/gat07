package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.domain.MemberStatus;
import ng.gat2027.grassroot.domain.PasswordResetToken;
import ng.gat2027.grassroot.domain.PromotionStage;
import ng.gat2027.grassroot.domain.Role;
import ng.gat2027.grassroot.repo.MemberRepository;
import ng.gat2027.grassroot.repo.PasswordResetTokenRepository;
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

    private static final long RESET_TOKEN_TTL_MINUTES = 30;

    private final MemberRepository members; private final PollingUnitRepository pus; private final LocationService locations;
    private final SettingsService settings; private final PasswordEncoder encoder; private final JdbcTemplate jdbc;
    private final PasswordResetTokenRepository resetTokens; private final MailService mail;

    public MemberService(MemberRepository members, PollingUnitRepository pus, LocationService locations, SettingsService settings, PasswordEncoder encoder, JdbcTemplate jdbc,
                          PasswordResetTokenRepository resetTokens, MailService mail) {
        this.members = members; this.pus = pus; this.locations = locations; this.settings = settings; this.encoder = encoder; this.jdbc = jdbc;
        this.resetTokens = resetTokens; this.mail = mail;
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
    /** The National Coordinator (Admin) can promote/demote any member to any role directly and instantly - no approval chain applies to them. */
    @Transactional
    public void setRole(Member admin, Long memberId, Role role) {
        if (admin.getId().equals(memberId) && role != Role.ADMIN) throw new MemberException("You cannot remove your own admin role");
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        m.setRole(role);
        clearPending(m);
        members.save(m);
    }

    /**
     * A State or Zonal Coordinator proposes a coordinator promotion, strictly below their own rank, for a member in
     * their own area. A State Coordinator's proposal must first pass the Zonal Coordinator overseeing that state's
     * zone, then the Admin (National Coordinator); a Zonal Coordinator's own proposal skips straight to the Admin.
     */
    @Transactional
    public void requestPromotion(Member requester, Long memberId, Role proposedRole) {
        int requesterRank = requester.getRole().coordinatorRank();
        int targetRank = proposedRole.coordinatorRank();
        if (requesterRank == 0) throw new MemberException("Only State or Zonal Coordinators can propose promotions");
        if (targetRank == 0 || targetRank >= requesterRank) throw new MemberException("You can only propose a role below your own position");
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        PromotionStage stage;
        if (requester.getRole() == Role.ZONAL_COORDINATOR) {
            if (!requester.getZoneId().equals(m.getZoneId())) throw new MemberException("You can only propose promotions for members in your own zone");
            stage = PromotionStage.NATIONAL;
        } else if (requester.getRole() == Role.COORDINATOR) {
            if (!requester.getStateId().equals(m.getStateId())) throw new MemberException("You can only propose promotions for members in your own state");
            stage = PromotionStage.ZONAL;
        } else {
            throw new MemberException("Only State or Zonal Coordinators can propose promotions");
        }
        m.setPendingRole(proposedRole);
        m.setPendingRoleRequestedBy(requester.getId());
        m.setPendingRoleStage(stage);
        m.setPendingRoleZonalApprovedBy(null);
        members.save(m);
    }

    /** Admin can approve a pending promotion at any stage; a Zonal Coordinator can only advance one of their own zone's ZONAL-stage requests to NATIONAL. */
    @Transactional
    public void approvePromotion(Member approver, Long memberId) {
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        if (m.getPendingRole() == null) throw new MemberException("This member has no pending promotion");
        if (approver.isAdmin()) { applyPendingRole(m); return; }
        if (approver.getRole() == Role.ZONAL_COORDINATOR && m.getPendingRoleStage() == PromotionStage.ZONAL) {
            if (!approver.getZoneId().equals(m.getZoneId())) throw new MemberException("You can only approve promotions for members in your own zone");
            m.setPendingRoleStage(PromotionStage.NATIONAL);
            m.setPendingRoleZonalApprovedBy(approver.getId());
            members.save(m);
            return;
        }
        throw new MemberException("You are not authorized to approve this promotion");
    }

    @Transactional
    public void rejectPromotion(Member approver, Long memberId) {
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        if (m.getPendingRole() == null) throw new MemberException("This member has no pending promotion");
        boolean allowed = approver.isAdmin() || (approver.getRole() == Role.ZONAL_COORDINATOR
            && m.getPendingRoleStage() == PromotionStage.ZONAL && approver.getZoneId().equals(m.getZoneId()));
        if (!allowed) throw new MemberException("You are not authorized to reject this promotion");
        clearPending(m);
        members.save(m);
    }

    private void applyPendingRole(Member m) { m.setRole(m.getPendingRole()); clearPending(m); members.save(m); }

    private void clearPending(Member m) { m.setPendingRole(null); m.setPendingRoleRequestedBy(null); m.setPendingRoleStage(null); m.setPendingRoleZonalApprovedBy(null); }

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

    // ----- self-service password reset (forgot password) -----
    /** Returns the masked email the link was sent to, or null if no matching/emailable account was found (caller shows a generic message in that case). */
    @Transactional
    public String requestPasswordReset(String phone, String resetBaseUrl) {
        Member m = members.findByPhone(Codes.normalizePhone(phone)).orElse(null);
        if (m == null || m.getEmail() == null || m.getEmail().isBlank() || !m.isActive()) return null;
        PasswordResetToken t = new PasswordResetToken();
        t.setMemberId(m.getId());
        t.setToken(Codes.secureToken());
        t.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(RESET_TOKEN_TTL_MINUTES));
        resetTokens.save(t);
        mail.sendPasswordReset(m.getEmail(), m.getFirstName(), resetBaseUrl + "?token=" + t.getToken());
        return maskEmail(m.getEmail());
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String local = email.substring(0, at), domain = email.substring(at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + domain;
    }

    public boolean isResetTokenValid(String token) {
        return token != null && resetTokens.findByToken(token).map(PasswordResetToken::isValid).orElse(false);
    }

    @Transactional
    public void resetPasswordWithToken(String token, String password) {
        if (password == null || password.length() < 6) throw new MemberException("Password must be at least 6 characters");
        PasswordResetToken t = resetTokens.findByToken(token == null ? "" : token).filter(PasswordResetToken::isValid)
            .orElseThrow(() -> new MemberException("This reset link is invalid or has expired. Please request a new one."));
        Member m = members.findById(t.getMemberId()).orElseThrow(() -> new MemberException("Member not found"));
        m.setPasswordHash(encoder.encode(password));
        members.save(m);
        t.setUsedAt(LocalDateTime.now(ZoneOffset.UTC));
        resetTokens.save(t);
        jdbc.update("DELETE FROM persistent_logins WHERE username = ?", m.getPhone());
    }

    private String uniqueReferralCode() {
        for (int i = 0; i < 20; i++) { String c = Codes.make("GAT", 6); if (!members.existsByReferralCode(c)) return c; }
        throw new IllegalStateException("Could not generate a referral code");
    }
}
