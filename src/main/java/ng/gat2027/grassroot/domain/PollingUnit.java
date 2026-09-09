package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity @Table(name = "polling_units") @Getter @Setter
public class PollingUnit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "ward_id", nullable = false) private Long wardId;
    private String code;
    @Column(nullable = false) private String name;
    private Double latitude;
    private Double longitude;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);

    public String getLabel() { return code == null || code.isBlank() ? name : code + " - " + name; }
}
