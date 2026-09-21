package ng.gat2027.grassroot.web.forms;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import ng.gat2027.grassroot.domain.Member;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter @Setter
public class ProfileForm extends LocationForm {
    @NotBlank(message = "First name is required") @Size(max = 80) private String firstName;
    @NotBlank(message = "Surname is required") @Size(max = 80) private String lastName;
    @Size(max = 80) private String otherName;
    @Pattern(regexp = "|Male|Female", message = "Gender must be Male or Female") private String gender;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate dob;
    @NotBlank(message = "Phone number is required") private String phone;
    @Email(message = "Enter a valid email address") @Size(max = 120) private String email;
    @Size(max = 300) private String address;
    @Size(max = 200, message = "Occupation is too long (200 characters max)") private String occupation;
    @Size(max = 60) private String education;
    @Size(max = 30) private String vin;
    private String photo;

    public static ProfileForm from(Member m) {
        ProfileForm f = new ProfileForm();
        f.setFirstName(m.getFirstName()); f.setLastName(m.getLastName()); f.setOtherName(m.getOtherName()); f.setGender(m.getGender());
        f.setDob(m.getDob()); f.setPhone(m.getPhone()); f.setEmail(m.getEmail()); f.setAddress(m.getAddress()); f.setOccupation(m.getOccupation());
        f.setEducation(m.getEducation()); f.setVin(m.getVin());
        f.setZoneId(m.getZoneId()); f.setStateId(m.getStateId()); f.setLgaId(m.getLgaId());
        f.setWardId(m.getWardId() == null ? null : String.valueOf(m.getWardId()));
        f.setPollingUnitId(m.getPollingUnitId() == null ? null : String.valueOf(m.getPollingUnitId()));
        f.setPuLatitude(m.getPuLatitude()); f.setPuLongitude(m.getPuLongitude());
        return f;
    }
}
