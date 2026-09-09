package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "lgas") @Getter @Setter
public class Lga {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "state_id", nullable = false) private Long stateId;
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String name;
}
