package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.PollingUnit;
import ng.gat2027.grassroot.domain.Ward;
import ng.gat2027.grassroot.repo.*;
import ng.gat2027.grassroot.web.forms.LocationForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {
    public static class LocationException extends RuntimeException { public LocationException(String m) { super(m); } }
    public record Resolved(Long zoneId, Long stateId, Long lgaId, Long wardId, Long pollingUnitId, Double lat, Double lng) {}

    private final StateRepository states; private final LgaRepository lgas; private final WardRepository wards; private final PollingUnitRepository pus;

    public LocationService(StateRepository states, LgaRepository lgas, WardRepository wards, PollingUnitRepository pus) {
        this.states = states; this.lgas = lgas; this.wards = wards; this.pus = pus;
    }

    /** Validate the zone → state → LGA → ward → PU chain, creating the ward / polling unit on demand when typed in. */
    @Transactional
    public Resolved resolve(LocationForm f, Long createdBy) {
        if (f.getZoneId() == null || f.getStateId() == null || f.getLgaId() == null) throw new LocationException("Zone, state and LGA are required");
        var st = states.findById(f.getStateId()).orElseThrow(() -> new LocationException("Unknown state"));
        if (!st.getZoneId().equals(f.getZoneId())) throw new LocationException("State does not belong to the selected zone");
        lgas.findByIdAndStateId(f.getLgaId(), f.getStateId()).orElseThrow(() -> new LocationException("LGA does not belong to the selected state"));

        Double lat = f.getPuLatitude(), lng = f.getPuLongitude();
        if (lat != null && (lat < 3 || lat > 15)) throw new LocationException("Latitude must be within Nigeria (about 4 to 14)");
        if (lng != null && (lng < 2 || lng > 16)) throw new LocationException("Longitude must be within Nigeria (about 2.5 to 15)");

        Long wardId = f.wardIdNumber();
        if (wardId == null) {
            String name = Codes.blankToNull(f.getNewWardName());
            if (name == null) throw new LocationException("Select a ward or enter the ward name");
            wardId = wards.findFirstByLgaIdAndNameIgnoreCase(f.getLgaId(), name).map(Ward::getId).orElseGet(() -> {
                Ward w = new Ward(); w.setLgaId(f.getLgaId()); w.setName(name); w.setCode(Codes.blankToNull(f.getNewWardCode()));
                return wards.save(w).getId();
            });
        } else {
            wards.findByIdAndLgaId(wardId, f.getLgaId()).orElseThrow(() -> new LocationException("Ward does not belong to the selected LGA"));
        }

        Long puId = f.pollingUnitIdNumber();
        PollingUnit pu;
        if (puId == null) {
            String name = Codes.blankToNull(f.getNewPuName());
            if (name == null) throw new LocationException("Select a polling unit or enter the polling unit name");
            final Long wid = wardId;
            pu = pus.findFirstByWardIdAndNameIgnoreCase(wid, name).orElseGet(() -> {
                PollingUnit p = new PollingUnit(); p.setWardId(wid); p.setName(name); p.setCode(Codes.blankToNull(f.getNewPuCode()));
                p.setLatitude(lat); p.setLongitude(lng); p.setCreatedBy(createdBy);
                return pus.save(p);
            });
        } else {
            pu = pus.findByIdAndWardId(puId, wardId).orElseThrow(() -> new LocationException("Polling unit does not belong to the selected ward"));
        }
        // First member to supply coordinates fills them in for the unit.
        if (lat != null && lng != null && (pu.getLatitude() == null || pu.getLongitude() == null)) {
            pu.setLatitude(lat); pu.setLongitude(lng); pus.save(pu);
        }
        return new Resolved(f.getZoneId(), f.getStateId(), f.getLgaId(), wardId, pu.getId(), lat, lng);
    }
}
