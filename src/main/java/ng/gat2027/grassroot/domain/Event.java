package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "events") @Getter @Setter
public class Event {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String title;
    @Column(nullable = false, unique = true) private String slug;
    @Enumerated(EnumType.STRING) @Column(name = "post_type", nullable = false) private PostType postType = PostType.EVENT;
    @Column(name = "event_date", nullable = false) private LocalDate eventDate;
    private String location;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(nullable = false) private String description;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "video_data") private String videoData;
    /** Null zoneId/stateId = a national event, visible and editable by everyone with event access. */
    @Column(name = "zone_id") private Long zoneId;
    @Column(name = "state_id") private Long stateId;
    @Column(nullable = false) private boolean published = true;
    /** False while a Media Coordinator's submission awaits Admin approval; everyone else's events are auto-approved. */
    @Column(nullable = false) private boolean approved = true;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);

    public boolean isUpcoming() { return !eventDate.isBefore(LocalDate.now()); }
    public boolean hasUploadedVideo() { return videoData != null && !videoData.isBlank(); }
    public boolean hasMedia() { return hasUploadedVideo(); }
}
