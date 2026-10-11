package ezbuild.api;

import com.stripe.exception.SignatureVerificationException;
import ezbuild.payment.PaymentService;
import ezbuild.service.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Provider-to-server callbacks: public and CSRF-free, but nothing is trusted until verified with the
 * provider.
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    /** SSLCommerz IPN: same verification path as the browser success callback. */
    @PostMapping("/sslcommerz/ipn")
    public ResponseEntity<String> ipn(@RequestParam Map<String, String> params) {
        try {
            String status = params.getOrDefault("status", "");
            if ("VALID".equalsIgnoreCase(status) || "VALIDATED".equalsIgnoreCase(status)) {
                paymentService.confirmSslCommerz(params);
            } else if ("FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
                paymentService.failOrCancel(params.getOrDefault("tran_id", ""), "CANCELLED".equalsIgnoreCase(status));
            }
            return ResponseEntity.ok("OK");
        } catch (NotFoundException e) {
            return ResponseEntity.badRequest().body("Unknown transaction");
        }
    }

    /** Stripe webhook: keep the raw body as a String (never re-serialise) for the signature check. */
    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> stripe(@RequestBody String payload,
                                         @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        if (signature == null) return ResponseEntity.badRequest().body("Missing signature");
        try {
            paymentService.handleStripeWebhook(payload, signature);
            return ResponseEntity.ok("OK");
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook with a bad signature rejected");
            return ResponseEntity.badRequest().body("Bad signature");
        }
    }
}
