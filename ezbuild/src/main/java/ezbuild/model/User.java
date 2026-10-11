package ezbuild.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Document("users")
@Getter
@Setter
public class User {

    @Id
    private String id;

    @NotBlank(message = "Username is required.")
    @Size(min = 3, max = 30, message = "Username must be 3 to 30 characters.")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Username may only contain letters, numbers and underscores.")
    @Indexed(unique = true)
    private String username;

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address, for example name@example.com.")
    @Indexed(unique = true)
    private String email;

    /** BCrypt hash only. */
    private String password;

    private Set<Role> roles = new LinkedHashSet<>();

    private boolean suspended;

    private int failedLogins;

    private Instant lockedUntil;

    @CreatedDate
    private Instant joinedAt;

    @Valid
    private Profile profile = new Profile();

    /** Embedded: small and always read with the user. */
    private List<Address> addresses = new ArrayList<>();

    /** Product ids (SKUs). */
    private Set<String> wishlist = new LinkedHashSet<>();

    public boolean isAdmin() {
        return roles.contains(Role.ROLE_ADMIN);
    }

    public boolean isStaff() {
        return roles.contains(Role.ROLE_STAFF) || isAdmin();
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public String displayLabel() {
        String name = profile == null ? null : profile.getDisplayName();
        return (name == null || name.isBlank()) ? username : name;
    }

    public Address defaultAddress() {
        return addresses.isEmpty() ? null : addresses.get(0);
    }
}
