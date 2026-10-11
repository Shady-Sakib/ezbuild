package ezbuild.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordForm {

    private String currentPassword;

    @NotBlank(message = "New password is required.")
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.")
    private String newPassword;

    private String confirmPassword;

    @AssertTrue(message = "Passwords do not match.")
    public boolean isPasswordConfirmed() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
