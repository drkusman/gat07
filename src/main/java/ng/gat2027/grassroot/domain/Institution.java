package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** A tertiary institution offered on the registration form's "Campus" dropdown once a Student
 *  applicant picks a Federal or State institution type (University, Polytechnic, College of Education). */
@Entity @Table(name = "institutions") @Getter @Setter
public class Institution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    /** "University", "Polytechnic" or "College of Education" - matches the registration form's institution-type options. */
    @Column(nullable = false) private String type;
    /** "Federal" or "State" - matches the registration form's ownership options. */
    @Column(nullable = false) private String ownership;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
}
