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
    @Column(name = "event_date", nullable = false) private LocalDate eventDate;
    private String location;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(nullable = false) private String description;
    @Column(name = "cover_image_url") private String coverImageUrl;
    @Column(name = "video_url") private String videoUrl;
    @Column(name = "photo_gallery_url") private String photoGalleryUrl;
    @Column(nullable = false) private boolean published = true;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);

    public boolean isUpcoming() { return !eventDate.isBefore(LocalDate.now()); }
    public boolean hasMedia() { return (photoGalleryUrl != null && !photoGalleryUrl.isBlank()) || (videoUrl != null && !videoUrl.isBlank()); }
}
