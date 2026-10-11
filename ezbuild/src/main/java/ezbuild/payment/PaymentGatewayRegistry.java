package ezbuild.payment;

import ezbuild.model.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Maps each digital PaymentMethod to its gateway bean. */
@Component
public class PaymentGatewayRegistry {

    private final Map<PaymentMethod, PaymentGateway> gateways = new EnumMap<>(PaymentMethod.class);

    public PaymentGatewayRegistry(List<PaymentGateway> all) {
        all.forEach(g -> gateways.put(g.method(), g));
    }

    public PaymentGateway get(PaymentMethod method) {
        PaymentGateway g = gateways.get(method);
        if (g == null) throw new IllegalArgumentException("No gateway for " + method);
        return g;
    }

    public boolean available(PaymentMethod method) {
        if (method == PaymentMethod.COD) return true;
        PaymentGateway g = gateways.get(method);
        return g != null && g.available();
    }
}
