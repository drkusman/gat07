package ng.gat2027.grassroot.repo;

import ng.gat2027.grassroot.domain.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
