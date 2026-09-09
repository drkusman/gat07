package ng.gat2027.grassroot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity @Table(name = "app_settings") @Getter @Setter
public class AppSetting {
    @Id @Column(name = "setting_key") private String key;
    @Column(name = "setting_value", nullable = false) private String value;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);

    public AppSetting() {}
    public AppSetting(String key, String value) { this.key = key; this.value = value; }
}
