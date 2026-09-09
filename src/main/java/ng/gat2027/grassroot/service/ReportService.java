package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.FieldReport;
import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.domain.ReportStatus;
import ng.gat2027.grassroot.repo.FieldReportRepository;
import ng.gat2027.grassroot.web.forms.ReportForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final FieldReportRepository reports;

    public ReportService(FieldReportRepository reports) { this.reports = reports; }

    @Transactional
    public FieldReport file(Member m, ReportForm f) {
        FieldReport r = new FieldReport();
        r.setMemberId(m.getId()); r.setCategory(f.getCategory()); r.setTitle(f.getTitle().trim()); r.setBody(f.getBody().trim());
        r.setStateId(m.getStateId()); r.setLgaId(m.getLgaId()); r.setWardId(m.getWardId()); r.setPollingUnitId(m.getPollingUnitId());
        r.setLatitude(f.getLatitude()); r.setLongitude(f.getLongitude());
        return reports.save(r);
    }

    @Transactional
    public void triage(Long id, ReportStatus status, String note) {
        FieldReport r = reports.findById(id).orElseThrow(() -> new IllegalArgumentException("Report not found"));
        r.setStatus(status);
        r.setAdminNote(note == null || note.isBlank() ? null : note.trim().length() > 1000 ? note.trim().substring(0, 1000) : note.trim());
        reports.save(r);
    }
}
