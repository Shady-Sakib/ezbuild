package ezbuild.model;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Profile {

    @Size(max = 60, message = "Display name can be at most 60 characters.")
    private String displayName;

    @Pattern(regexp = "^$|^(\\+?88)?01[3-9]\\d{8}$", message = "Enter a valid Bangladeshi mobile number, for example 01712345678.")
    private String phone;
}
