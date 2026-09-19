package ng.gat2027.grassroot.domain;

public enum Role {
    MEMBER, POLLING_UNIT_COORDINATOR, WARD_COORDINATOR, LGA_COORDINATOR, COORDINATOR, ZONAL_COORDINATOR, GRAND_PATRON, ADMIN;

    /** Seniority within the operational coordinator ladder (higher = more senior); 0 for roles outside that ladder (MEMBER, GRAND_PATRON, ADMIN). */
    public int coordinatorRank() {
        return switch (this) {
            case POLLING_UNIT_COORDINATOR -> 1;
            case WARD_COORDINATOR -> 2;
            case LGA_COORDINATOR -> 3;
            case COORDINATOR -> 4;
            case ZONAL_COORDINATOR -> 5;
            default -> 0;
        };
    }
}
