package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "event_photos") @Getter @Setter
public class EventPhoto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false) private Long eventId;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "image_data", nullable = false) private String imageData;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
}
