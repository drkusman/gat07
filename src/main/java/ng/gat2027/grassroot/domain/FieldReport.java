package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity @Table(name = "field_reports") @Getter @Setter
public class FieldReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ReportCategory category;
    @Column(nullable = false) private String title;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(nullable = false) private String body;
    @Column(name = "state_id") private Long stateId;
    @Column(name = "lga_id") private Long lgaId;
    @Column(name = "ward_id") private Long wardId;
    @Column(name = "polling_unit_id") private Long pollingUnitId;
    private Double latitude;
    private Double longitude;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ReportStatus status = ReportStatus.OPEN;
    @Column(name = "admin_note") private String adminNote;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
}
