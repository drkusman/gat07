package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "states") @Getter @Setter
public class State {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "zone_id", nullable = false) private Long zoneId;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false, unique = true) private String name;
    private String capital;
}
