package ezbuild.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignUpForm {

    @NotBlank(message = "Username is required.")
    @Size(min = 3, max = 30, message = "Username must be 3 to 30 characters.")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Username may only contain letters, numbers and underscores.")
    private String username;

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address, for example name@example.com.")
    private String email;

    @NotBlank(message = "Password is required.")
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.")
    private String password;

    private String confirmPassword;

    @AssertTrue(message = "Passwords do not match.")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}
