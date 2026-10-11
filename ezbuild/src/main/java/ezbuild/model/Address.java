package ezbuild.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Embedded in users (saved addresses) and orders (shipping snapshot). */
@Getter
@Setter
public class Address {

    @NotBlank(message = "Full name is required.")
    @Size(max = 60)
    private String fullName;

    @NotBlank(message = "Phone is required.")
    @Pattern(regexp = "^(\\+?88)?01[3-9]\\d{8}$", message = "Enter a valid Bangladeshi mobile number, for example 01712345678.")
    private String phone;

    @NotBlank(message = "Address is required.")
    @Size(max = 160)
    private String line1;

    @Size(max = 60)
    private String area;

    @NotBlank(message = "City is required.")
    @Size(max = 40)
    private String city;

    @Size(max = 10)
    private String postcode;

    @NotNull(message = "Choose inside or outside Dhaka.")
    private ShippingZone zone = ShippingZone.INSIDE_DHAKA;

    public String oneLine() {
        StringBuilder sb = new StringBuilder(line1 == null ? "" : line1);
        if (area != null && !area.isBlank()) sb.append(", ").append(area);
        if (city != null && !city.isBlank()) sb.append(", ").append(city);
        if (postcode != null && !postcode.isBlank()) sb.append(" ").append(postcode);
        return sb.toString();
    }
}
