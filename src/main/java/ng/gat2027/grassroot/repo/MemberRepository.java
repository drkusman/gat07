package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import ng.gat2027.grassroot.domain.PromotionStage;
import ng.gat2027.grassroot.domain.Role;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByPhone(String phone);
    Optional<Member> findByReferralCodeIgnoreCase(String code);
    boolean existsByPhone(String phone);
    boolean existsByReferralCode(String code);
    boolean existsByRole(Role role);
    long countByReferredBy(Long memberId);
    List<Member> findByReferredByOrderByCreatedAtDesc(Long memberId);
    long countByPollingUnitId(Long id);
    long countByWardId(Long id);
    long countByLgaId(Long id);
    long countByStateId(Long id);
    long countByPendingRoleIsNotNull();
    List<Member> findByPendingRoleIsNotNullOrderByIdDesc();
    long countByPendingRoleIsNotNullAndZoneIdAndPendingRoleStage(Long zoneId, PromotionStage stage);
    List<Member> findByPendingRoleIsNotNullAndZoneIdAndPendingRoleStageOrderByIdDesc(Long zoneId, PromotionStage stage);
    @Query("select coalesce(max(m.id), 0) from Member m") long maxId();
}
