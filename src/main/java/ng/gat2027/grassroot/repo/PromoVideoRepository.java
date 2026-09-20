package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.PromoVideo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromoVideoRepository extends JpaRepository<PromoVideo, Long> {
    List<PromoVideo> findAllByOrderByIdAsc();
}
