package ezbuild.payment;

import ezbuild.model.Order;
import ezbuild.model.Payment;
import ezbuild.model.PaymentMethod;

/** Hosted checkout only: card data never touches our server. */
public interface PaymentGateway {

    PaymentMethod method();

    boolean available();

    /** Creates the provider session; the browser is then sent to InitResult.redirectUrl. */
    InitResult init(Order order, Payment payment);

    RefundResult refund(Payment payment, long amountBdt, String reason);

    record InitResult(boolean ok, String redirectUrl, String providerRef, String error) {
        public static InitResult ok(String url, String ref) {
            return new InitResult(true, url, ref, null);
        }

        public static InitResult failed(String error) {
            return new InitResult(false, null, null, error);
        }
    }

    record RefundResult(boolean ok, boolean completed, String ref, String error) { }
}
