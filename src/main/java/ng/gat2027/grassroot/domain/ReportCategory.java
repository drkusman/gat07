package ng.gat2027.grassroot.domain;

public enum ReportCategory {
    ACTIVITY("Activity / meeting / rally"), VOTER_MOBILISATION("Voter mobilisation update"), INCIDENT("Incident"), COMPLAINT("Complaint"), OTHER("Other");

    public final String label;
    ReportCategory(String label) { this.label = label; }
    public String getLabel() { return label; }
    public boolean isUrgent() { return this == INCIDENT || this == COMPLAINT; }
}
