package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.FieldReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FieldReportRepository extends JpaRepository<FieldReport, Long> {
    List<FieldReport> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    long countByMemberId(Long memberId);
}
