package ng.gat2027.grassroot.web.forms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SupportGroupForm {
    @NotBlank(message = "Name of the group is required") @Size(max = 200) private String groupName;
    @NotBlank(message = "Name of the head is required") @Size(max = 150) private String headName;
    @NotBlank(message = "Designation of the head is required") @Size(max = 150) private String headTitle;
    @NotBlank(message = "Head office is required") @Size(max = 300) private String headOffice;
    @NotBlank(message = "Contact number is required") private String contactPhone;
    // Kept as text (not Integer) so a blank/non-numeric entry fails with our own friendly message
    // instead of Spring's generic type-conversion error - see RegisterForm.institutionId for the same pattern.
    @NotBlank(message = "Member strength is required") private String memberStrength;

    public Integer memberStrengthNumber() {
        try { return memberStrength == null || memberStrength.isBlank() ? null : Integer.parseInt(memberStrength.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
