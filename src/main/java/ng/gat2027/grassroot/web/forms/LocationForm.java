package ng.gat2027.grassroot.web.forms;

import lombok.Getter;
import lombok.Setter;

/** Zone -> state -> LGA -> ward -> polling unit selection, with "type it in" fallbacks and GPS. */
@Getter @Setter
public class LocationForm {
    private Long zoneId;
    private Long stateId;
    private Long lgaId;
    private String wardId;         // numeric id or "__new"
    private String newWardName;
    private String newWardCode;
    private String pollingUnitId;  // numeric id or "__new"
    private String newPuName;
    private String newPuCode;
    private Double puLatitude;
    private Double puLongitude;

    public Long wardIdNumber() { return parse(wardId); }
    public Long pollingUnitIdNumber() { return parse(pollingUnitId); }
    private static Long parse(String v) { try { return v == null || v.isBlank() || v.startsWith("__") ? null : Long.parseLong(v.trim()); } catch (NumberFormatException e) { return null; } }
}
