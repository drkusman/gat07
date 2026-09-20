package ng.gat2027.grassroot.web.forms;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter @Setter
public class RegisterForm extends LocationForm {
    @NotBlank(message = "First name is required") @Size(max = 80) private String firstName;
    @NotBlank(message = "Surname is required") @Size(max = 80) private String lastName;
    @Size(max = 80) private String otherName;
    @Pattern(regexp = "|Male|Female", message = "Gender must be Male or Female") private String gender;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate dob;
    @NotBlank(message = "Phone number is required") private String phone;
    @Email(message = "Enter a valid email address") @Size(max = 120) private String email;
    @Size(max = 300) private String address;
    @Size(max = 100) private String occupation;
    private String institutionId; // numeric id, or blank when not a student at a Federal/State tertiary institution
    @Size(max = 60) private String education;
    @Size(max = 30) private String vin;
    @Size(max = 20) private String maritalStatus;
    private boolean specialNeeds;
    private String photo;
    @NotBlank(message = "Password is required") @Size(min = 6, max = 200, message = "Password must be at least 6 characters") private String password;
    private String password2;
    @Size(max = 20) private String referralCode;
    private boolean consent;

    public Long institutionIdNumber() {
        try { return institutionId == null || institutionId.isBlank() ? null : Long.parseLong(institutionId.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
