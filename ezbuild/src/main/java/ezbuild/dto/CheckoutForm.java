package ezbuild.dto;

import ezbuild.model.Address;
import ezbuild.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Form object for /checkout (also the body of POST /api/orders). */
@Getter
@Setter
public class CheckoutForm {

    @Valid
    @NotNull
    private Address address = new Address();

    @NotNull(message = "Choose a payment method.")
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    @Size(max = 200, message = "Keep the note under 200 characters.")
    private String note;

    private boolean saveAddress = true;
}
