package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.SupportGroup;
import ng.gat2027.grassroot.repo.SupportGroupRepository;
import ng.gat2027.grassroot.web.forms.SupportGroupForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SupportGroupService {
    public static class SupportGroupException extends RuntimeException { public SupportGroupException(String m) { super(m); } }

    private final SupportGroupRepository groups;

    public SupportGroupService(SupportGroupRepository groups) { this.groups = groups; }

    @Transactional
    public SupportGroup register(SupportGroupForm f) {
        String phone = Codes.normalizePhone(f.getContactPhone());
        if (!Codes.validPhone(phone)) throw new SupportGroupException("Enter a valid Nigerian phone number (e.g. 0803 123 4567)");
        Integer strength = f.memberStrengthNumber();
        if (strength == null || strength < 1) throw new SupportGroupException("Enter the group's member strength as a whole number");

        SupportGroup g = new SupportGroup();
        g.setGroupName(f.getGroupName().trim());
        g.setHeadName(f.getHeadName().trim());
        g.setHeadTitle(f.getHeadTitle().trim());
        g.setHeadOffice(f.getHeadOffice().trim());
        g.setContactPhone(phone);
        g.setMemberStrength(strength);
        return groups.save(g);
    }

    public List<SupportGroup> all() { return groups.findAllByOrderByCreatedAtDesc(); }
}
