package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.domain.MemberStatus;
import ng.gat2027.grassroot.domain.PasswordResetToken;
import ng.gat2027.grassroot.domain.Position;
import ng.gat2027.grassroot.domain.PromotionStage;
import ng.gat2027.grassroot.domain.Role;
import ng.gat2027.grassroot.repo.MemberRepository;
import ng.gat2027.grassroot.repo.PasswordResetTokenRepository;
import ng.gat2027.grassroot.repo.PollingUnitRepository;
import ng.gat2027.grassroot.repo.PositionRepository;
import ng.gat2027.grassroot.web.forms.ProfileForm;
import ng.gat2027.grassroot.web.forms.RegisterForm;
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
    private final SettingsService settings; private final PasswordEncoder encoder;
    private final PasswordResetTokenRepository resetTokens; private final MailService mail; private final WhatsAppService whatsApp;
    private final TermiiService sms; private final PositionRepository positions;

    public MemberService(MemberRepository members, PollingUnitRepository pus, LocationService locations, SettingsService settings, PasswordEncoder encoder,
                          PasswordResetTokenRepository resetTokens, MailService mail, WhatsAppService whatsApp, TermiiService sms, PositionRepository positions) {
        this.members = members; this.pus = pus; this.locations = locations; this.settings = settings; this.encoder = encoder;
        this.resetTokens = resetTokens; this.mail = mail; this.whatsApp = whatsApp; this.sms = sms; this.positions = positions;
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
        m.setSpecialNeeds(f.isSpecialNeeds());
        m.setMaritalStatus(Codes.blankToNull(f.getMaritalStatus()));
        m.setInstitutionId(f.institutionIdNumber());
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

    /** Stores a photo/scan of the appointee's signed "Acceptance of Appointment" page (the second page of
     *  their appointment letter - see AppointmentLetterService). Re-uploading replaces the previous copy. */
    @Transactional
    public void uploadAcceptanceLetter(Member m, String dataUrl) {
        if (m.getPositionId() == null) throw new MemberException("No position assigned yet");
        if (dataUrl == null || !dataUrl.startsWith("data:image/") || dataUrl.length() > 4_000_000)
            throw new MemberException("Please upload a clear photo or scan of the signed acceptance page (image, under a few MB)");
        m.setAcceptanceData(dataUrl);
        m.setAcceptanceUploadedAt(LocalDateTime.now(ZoneOffset.UTC));
        members.save(m);
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

    /** Assigns a member to an organisational Position (Admin-only, instant - the same as a direct role change).
     *  The member's Role is derived from the position's title: positions that match one of the system's operational
     *  roles (see OrganizationService.roleForPositionTitle) carry that role; every other position leaves them a
     *  plain Member. Passing a null positionId clears the assignment without touching the member's current role. */
    @Transactional
    public void assignPosition(Member admin, Long memberId, Long positionId) {
        if (admin.getId().equals(memberId)) throw new MemberException("You cannot change your own position");
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        boolean changed = !java.util.Objects.equals(m.getPositionId(), positionId) || m.getOtherPositionTitle() != null;
        m.setOtherPositionTitle(null);
        m.setOtherPositionTerms(null);
        if (positionId == null) {
            m.setPositionId(null);
            if (changed) clearAcceptance(m);
            members.save(m);
            return;
        }
        Position position = positions.findById(positionId).orElseThrow(() -> new MemberException("Position not found"));
        m.setPositionId(positionId);
        m.setRole(OrganizationService.roleForPositionTitle(position.getTitle()));
        if (changed) clearAcceptance(m);
        clearPending(m);
        members.save(m);
    }

    /** An ad hoc "Others" appointment: admin supplies both the position title and the Terms of Reference text
     *  printed on the letter, for one-off special appointments outside the standing organisational structure.
     *  Mutually exclusive with a standard Position - assigning one clears the other. A blank title clears it. */
    @Transactional
    public void assignOtherPosition(Member admin, Long memberId, String title, String termsOfReference) {
        if (admin.getId().equals(memberId)) throw new MemberException("You cannot change your own position");
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        String cleanTitle = Codes.blankToNull(title);
        boolean changed = !java.util.Objects.equals(m.getOtherPositionTitle(), cleanTitle) || m.getPositionId() != null;
        m.setPositionId(null);
        if (cleanTitle == null) {
            m.setOtherPositionTitle(null);
            m.setOtherPositionTerms(null);
            if (changed) clearAcceptance(m);
            members.save(m);
            return;
        }
        if (cleanTitle.length() > 150) throw new MemberException("Position title is too long (150 characters max)");
        String terms = Codes.blankToNull(termsOfReference);
        if (terms != null && terms.length() > 3000) throw new MemberException("Terms of Reference is too long (3,000 characters max)");
        m.setOtherPositionTitle(cleanTitle);
        m.setOtherPositionTerms(terms);
        m.setRole(Role.MEMBER);
        if (changed) clearAcceptance(m);
        clearPending(m);
        members.save(m);
    }

    private void clearAcceptance(Member m) {
        m.setAcceptanceData(null);
        m.setAcceptanceUploadedAt(null);
    }

    /**
     * A Zonal, State, or LGA Coordinator recommends a coordinator promotion, strictly below their own rank, for a
     * member within their own area (zone/state/LGA respectively). Every recommendation goes straight to the Admin
     * (National Coordinator) for final approval - there is no intermediate approval stage.
     */
    @Transactional
    public void requestPromotion(Member requester, Long memberId, Role proposedRole) {
        int requesterRank = requester.getRole().coordinatorRank();
        int targetRank = proposedRole.coordinatorRank();
        if (targetRank == 0 || targetRank >= requesterRank) throw new MemberException("You can only recommend a role below your own position");
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        if (requester.getRole() == Role.ZONAL_COORDINATOR) {
            if (!requester.getZoneId().equals(m.getZoneId())) throw new MemberException("You can only recommend members in your own zone");
        } else if (requester.getRole() == Role.COORDINATOR) {
            if (!requester.getStateId().equals(m.getStateId())) throw new MemberException("You can only recommend members in your own state");
        } else if (requester.getRole() == Role.LGA_COORDINATOR) {
            if (!requester.getLgaId().equals(m.getLgaId())) throw new MemberException("You can only recommend members in your own LGA");
        } else {
            throw new MemberException("Only LGA, State, or Zonal Coordinators can recommend promotions");
        }
        m.setPendingRole(proposedRole);
        m.setPendingRoleRequestedBy(requester.getId());
        m.setPendingRoleStage(PromotionStage.NATIONAL);
        m.setPendingRoleZonalApprovedBy(null);
        members.save(m);
    }

    /** Only the Admin (National Coordinator) gives final approval on a recommended promotion. */
    @Transactional
    public void approvePromotion(Member approver, Long memberId) {
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        if (m.getPendingRole() == null) throw new MemberException("This member has no pending promotion");
        if (!approver.isAdmin()) throw new MemberException("Only the National Coordinator (Admin) can approve promotions");
        applyPendingRole(m);
    }

    @Transactional
    public void rejectPromotion(Member approver, Long memberId) {
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        if (m.getPendingRole() == null) throw new MemberException("This member has no pending promotion");
        if (!approver.isAdmin()) throw new MemberException("Only the National Coordinator (Admin) can reject promotions");
        clearPending(m);
        members.save(m);
    }

    private void applyPendingRole(Member m) { m.setRole(m.getPendingRole()); clearPending(m); members.save(m); }

    private void clearPending(Member m) { m.setPendingRole(null); m.setPendingRoleRequestedBy(null); m.setPendingRoleStage(null); m.setPendingRoleZonalApprovedBy(null); }

    /** A suspended member's remember-me cookie stays valid but auto-login is still blocked, since
     *  MemberPrincipal.isEnabled()/isAccountNonLocked() re-check the current status on every attempt. */
    @Transactional
    public void setStatus(Member admin, Long memberId, MemberStatus status) {
        if (admin.getId().equals(memberId)) throw new MemberException("You cannot suspend your own account");
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        m.setStatus(status); members.save(m);
    }

    /** Admin can reset anyone's password; State/LGA/Ward Coordinators can reset it for members in their own
     *  jurisdiction only - the intended path for members with no working phone/email channel of their own,
     *  since their coordinator can verify who they are in person. */
    @Transactional
    public void resetPassword(Member actor, Long memberId, String password) {
        if (password == null || password.length() < 6) throw new MemberException("Password must be at least 6 characters");
        Member m = members.findById(memberId).orElseThrow(() -> new MemberException("Member not found"));
        if (!actor.isAdmin()) {
            switch (actor.getRole()) {
                case COORDINATOR -> { if (!actor.getStateId().equals(m.getStateId())) throw new MemberException("You can only reset passwords for members in your own state"); }
                case LGA_COORDINATOR -> { if (!actor.getLgaId().equals(m.getLgaId())) throw new MemberException("You can only reset passwords for members in your own LGA"); }
                case WARD_COORDINATOR -> { if (!actor.getWardId().equals(m.getWardId())) throw new MemberException("You can only reset passwords for members in your own ward"); }
                default -> throw new MemberException("Only Admin, State, LGA, or Ward Coordinators can reset a member's password");
            }
        }
        m.setPasswordHash(encoder.encode(password)); members.save(m);
    }

    // ----- self-service password reset (forgot password) -----
    private static final int OTP_TTL_MINUTES = 10;

    /** otpPending: true when a code was sent via WhatsApp - the caller should show a "enter your code"
     *  form (POST to verifyResetCode) instead of "check your phone/email for a link". */
    public record ResetRequestResult(String maskedEmail, String maskedPhone, String devModeLink, boolean otpPending) {}

    /** Nothing found -> all fields null (caller shows a generic message either way, so this can't be used to
     *  probe who's registered). Every member has a phone number, so SMS (Termii, a clickable link) is tried
     *  first, then WhatsApp (a typed-in code, since Meta only approves Authentication-category templates for
     *  this kind of content, and that category can't carry a link); email (if on file) is the fallback after
     *  that; if nothing is configured, the caller gets the raw link back to show directly (dev-mode only). */
    @Transactional
    public ResetRequestResult requestPasswordReset(String phone, String resetBaseUrl) {
        Member m = members.findByPhone(Codes.normalizePhone(phone)).orElse(null);
        if (m == null || !m.isActive()) return new ResetRequestResult(null, null, null, false);
        PasswordResetToken t = new PasswordResetToken();
        t.setMemberId(m.getId());
        t.setToken(Codes.secureToken());
        t.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(RESET_TOKEN_TTL_MINUTES));
        String link = resetBaseUrl + "?token=" + t.getToken();
        String phoneE164 = Codes.nigeriaE164(m.getPhone());

        if (sms.isConfigured() && sms.sendPasswordResetLink(phoneE164, m.getFirstName(), link)) {
            resetTokens.save(t);
            return new ResetRequestResult(null, maskPhone(m.getPhone()), null, false);
        }
        if (whatsApp.isConfigured()) {
            String code = Codes.numericCode(6);
            t.setOtpCode(code);
            t.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(OTP_TTL_MINUTES));
            if (whatsApp.sendPasswordResetCode(phoneE164, code)) {
                resetTokens.save(t);
                return new ResetRequestResult(null, maskPhone(m.getPhone()), null, true);
            }
            t.setOtpCode(null);
            t.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(RESET_TOKEN_TTL_MINUTES));
        }
        boolean hasEmail = m.getEmail() != null && !m.getEmail().isBlank();
        if (hasEmail) {
            mail.sendPasswordReset(m.getEmail(), m.getFirstName(), link);
            if (mail.isConfigured()) {
                resetTokens.save(t);
                return new ResetRequestResult(maskEmail(m.getEmail()), null, null, false);
            }
        }
        resetTokens.save(t);
        return new ResetRequestResult(null, null, link, false);
    }

    /** Resolves a WhatsApp-delivered OTP code back to the underlying reset token, so the rest of the flow
     *  (the /reset-password page) works identically regardless of which channel delivered it. */
    public String verifyResetCode(String phone, String code) {
        Member m = members.findByPhone(Codes.normalizePhone(phone)).orElse(null);
        if (m == null) throw new MemberException("Invalid or expired code");
        String cleanCode = code == null ? "" : code.trim();
        PasswordResetToken t = resetTokens.findFirstByMemberIdAndOtpCodeOrderByCreatedAtDesc(m.getId(), cleanCode)
            .filter(PasswordResetToken::isValid)
            .orElseThrow(() -> new MemberException("Invalid or expired code"));
        return t.getToken();
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 4) + "*".repeat(phone.length() - 6) + phone.substring(phone.length() - 2);
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

    /** Also invalidates any remember-me cookies for this member: the cookie's signature is derived from
     *  the password hash, so it stops verifying the moment the hash changes here. */
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
    }

    private String uniqueReferralCode() {
        for (int i = 0; i < 20; i++) { String c = Codes.make("GAT", 6); if (!members.existsByReferralCode(c)) return c; }
        throw new IllegalStateException("Could not generate a referral code");
    }
}
