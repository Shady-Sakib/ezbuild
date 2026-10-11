package ezbuild.payment;

import ezbuild.config.AppProperties;
import ezbuild.model.Order;
import ezbuild.model.Payment;
import ezbuild.model.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Offline demo provider: redirects to our own /payment/sandbox page with Approve / Decline buttons. No card
 * fields.
 */
@Component
@RequiredArgsConstructor
public class SandboxGateway implements PaymentGateway {

    private final AppProperties props;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.SANDBOX;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public InitResult init(Order order, Payment payment) {
        return InitResult.ok(props.baseUrl() + "/payment/sandbox/" + payment.getTranId(), "SBX-" + payment.getTranId());
    }

    @Override
    public RefundResult refund(Payment payment, long amountBdt, String reason) {
        return new RefundResult(true, true, "SBX-RF-" + payment.getTranId(), null);
    }
}
