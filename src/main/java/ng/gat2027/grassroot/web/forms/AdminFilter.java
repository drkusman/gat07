package ng.gat2027.grassroot.web.forms;

import lombok.Getter;
import lombok.Setter;

/** Coordination-centre filter bar (bound from query parameters, shared by every admin page). */
@Getter @Setter
public class AdminFilter {
    private Long zoneId;
    private Long stateId;
    private Long lgaId;
    private Long wardId;
    private Long pollingUnitId;
    private String gender;
    private String q;
    private String level;   // breakdown level
    private String status;  // reports status
    private Integer page;

    /** Query string (without page) to preserve the selection across links. */
    public String qs() {
        StringBuilder sb = new StringBuilder();
        append(sb, "zoneId", zoneId); append(sb, "stateId", stateId); append(sb, "lgaId", lgaId); append(sb, "wardId", wardId);
        append(sb, "pollingUnitId", pollingUnitId); append(sb, "gender", gender); append(sb, "q", q); append(sb, "level", level); append(sb, "status", status);
        return sb.toString();
    }
    public String qsWith(String key, String value) {
        AdminFilter c = new AdminFilter();
        c.zoneId = zoneId; c.stateId = stateId; c.lgaId = lgaId; c.wardId = wardId; c.pollingUnitId = pollingUnitId; c.gender = gender; c.q = q; c.level = level; c.status = status;
        switch (key) { case "level" -> c.level = value; case "status" -> c.status = value; default -> {} }
        return c.qs();
    }
    private static void append(StringBuilder sb, String k, Object v) {
        if (v == null || v.toString().isBlank()) return;
        sb.append(sb.length() == 0 ? "?" : "&").append(k).append('=').append(java.net.URLEncoder.encode(v.toString(), java.nio.charset.StandardCharsets.UTF_8));
    }
}
