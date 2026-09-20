package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity @Table(name = "promo_videos") @Getter @Setter
public class PromoVideo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "youtube_id", nullable = false) private String youtubeId;
    private String title;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
    /** When the video drops out of the home page loop. Null on rows created before this field existed — treated as createdAt + 24h. */
    @Column(name = "expires_at") private LocalDateTime expiresAt;
    /** While this is in the future, the video plays exclusively (no other video in the loop) until it passes. */
    @Column(name = "priority_until") private LocalDateTime priorityUntil;

    public LocalDateTime effectiveExpiresAt() { return expiresAt != null ? expiresAt : createdAt.plusHours(24); }
    public boolean isExpired() { return effectiveExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC)); }
    public boolean isPriorityActive() { return priorityUntil != null && priorityUntil.isAfter(LocalDateTime.now(ZoneOffset.UTC)); }
}
