package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity @Table(name = "announcements") @Getter @Setter
public class Announcement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String title;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(nullable = false) private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AnnouncementAudience audience;
    /** Only meaningful when audience == MEMBERS; null means nationwide (every member sees it, not just one state). */
    @Column(name = "state_id") private Long stateId;
    @Column(nullable = false) private boolean published = true;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now(ZoneOffset.UTC);
}
