package ng.gat2027.grassroot.web.forms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import ng.gat2027.grassroot.domain.ReportCategory;

@Getter @Setter
public class ReportForm {
    @NotNull(message = "Choose a report category") private ReportCategory category;
    @NotBlank(message = "Title is required") @Size(min = 3, max = 160) private String title;
    @NotBlank(message = "Details are required") @Size(min = 3, max = 5000) private String body;
    private Double latitude;
    private Double longitude;
}
