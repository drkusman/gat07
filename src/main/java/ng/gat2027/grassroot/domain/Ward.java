package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "wards") @Getter @Setter
public class Ward {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "lga_id", nullable = false) private Long lgaId;
    private String code;
    @Column(nullable = false) private String name;
}
