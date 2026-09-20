package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "members") @Getter @Setter
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_code", nullable = false, unique = true) private String memberCode;
    @Column(name = "referral_code", nullable = false, unique = true) private String referralCode;
    @Column(name = "referred_by") private Long referredBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role = Role.MEMBER;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MemberStatus status = MemberStatus.ACTIVE;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    @Column(name = "other_name") private String otherName;
    private String gender;
    private LocalDate dob;
    @Column(nullable = false, unique = true) private String phone;
    private String email;
    private String address;
    private String occupation;
    private String education;
    private String vin;
    @Column(name = "special_needs", nullable = false) private boolean specialNeeds;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) private String photo;
    @Column(name = "zone_id") private Long zoneId;
    @Column(name = "state_id") private Long stateId;
    @Column(name = "lga_id") private Long lgaId;
    @Column(name = "ward_id") private Long wardId;
    @Column(name = "polling_unit_id") private Long pollingUnitId;
    @Column(name = "pu_latitude") private Double puLatitude;
    @Column(name = "pu_longitude") private Double puLongitude;
    /** A promotion proposed for this member, working through its approval chain (ZONAL then NATIONAL, or straight to
     *  NATIONAL when a Zonal Coordinator is the one proposing it) before it actually takes effect. */
    @Enumerated(EnumType.STRING) @Column(name = "pending_role") private Role pendingRole;
    @Column(name = "pending_role_requested_by") private Long pendingRoleRequestedBy;
    @Enumerated(EnumType.STRING) @Column(name = "pending_role_stage") private PromotionStage pendingRoleStage;
    @Column(name = "pending_role_zonal_approved_by") private Long pendingRoleZonalApprovedBy;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);

    public String getFullName() {
        StringBuilder sb = new StringBuilder(firstName);
        if (otherName != null && !otherName.isBlank()) sb.append(' ').append(otherName);
        return sb.append(' ').append(lastName).toString();
    }
    public String getDisplayName() { return firstName + " " + lastName; }
    public String getInitials() { return ("" + firstName.charAt(0) + lastName.charAt(0)).toUpperCase(); }
    public boolean isStaff() {
        return role == Role.ADMIN || role == Role.COORDINATOR || role == Role.ZONAL_COORDINATOR || role == Role.GRAND_PATRON
            || role == Role.LGA_COORDINATOR || role == Role.WARD_COORDINATOR || role == Role.POLLING_UNIT_COORDINATOR;
    }
    public boolean isAdmin() { return role == Role.ADMIN; }
    public boolean isActive() { return status == MemberStatus.ACTIVE; }
}
