package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A support group, association, or union that has registered its affiliation with GAT 2027
 *  (e.g. a market women's association, a professional union, a community development union). */
@Entity @Table(name = "support_groups") @Getter @Setter
public class SupportGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "group_name", nullable = false) private String groupName;
    @Column(name = "head_name", nullable = false) private String headName;
    /** The head's title/designation within the group (e.g. National Chairman, President, National Coordinator). */
    @Column(name = "head_title", nullable = false) private String headTitle;
    @Column(name = "head_office", nullable = false) private String headOffice;
    @Column(name = "contact_phone", nullable = false) private String contactPhone;
    @Column(name = "member_strength", nullable = false) private int memberStrength;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
}
