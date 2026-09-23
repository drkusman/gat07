package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity @Table(name = "password_reset_tokens") @Getter @Setter
public class PasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(nullable = false, unique = true) private String token;
    /** Short numeric code sent via WhatsApp's Authentication template (which can't carry a link) - the
     *  member types this in, and it resolves back to this same token for the rest of the reset flow. */
    @Column(name = "otp_code") private String otpCode;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "used_at") private LocalDateTime usedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    public boolean isValid() { return usedAt == null && expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC)); }
}
