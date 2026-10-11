package ezbuild.payment;

import ezbuild.config.AppProperties;
import ezbuild.model.Order;
import ezbuild.model.Payment;
import ezbuild.model.PaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SSLCommerz v4 (developer.sslcommerz.com/doc/v4). No SDK: plain RestClient.
 *  - init:      POST /gwprocess/v4/api.php (form) -> GatewayPageURL + sessionkey
 *  - validate:  GET  /validator/api/validationserverAPI.php?val_id=...   (mandatory check)
 *  - query:     GET  /validator/api/merchantTransIDvalidationAPI.php?tran_id=...  (reconciliation)
 *  - refund:    GET  /validator/api/merchantTransIDvalidationAPI.php?bank_tran_id=...&refund_trans_id=...
 * store_passwd is never logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SslCommerzGateway implements PaymentGateway {

    private final RestClient restClient;
    private final AppProperties props;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.SSLCOMMERZ;
    }

    @Override
    public boolean available() {
        return props.sslcommerz().configured();
    }

    @Override
    public InitResult init(Order order, Payment payment) {
        if (!available()) return InitResult.failed("SSLCommerz is not configured (set SSLC_STORE_ID and SSLC_STORE_PASS).");
        String base = props.baseUrl();
        var s = order.getShipping();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("store_id", props.sslcommerz().storeId());
        form.add("store_passwd", props.sslcommerz().storePass());
        form.add("total_amount", amount(order.getTotal()));
        form.add("currency", "BDT");
        form.add("tran_id", payment.getTranId());
        form.add("success_url", base + "/payment/sslcommerz/success");
        form.add("fail_url", base + "/payment/sslcommerz/fail");
        form.add("cancel_url", base + "/payment/sslcommerz/cancel");
        form.add("ipn_url", base + "/api/payments/sslcommerz/ipn");
        form.add("product_name", "PC components (" + order.itemCount() + " items)");
        form.add("product_category", "Computer");
        form.add("product_profile", "physical-goods");
        form.add("shipping_method", "Courier");
        form.add("num_of_item", String.valueOf(order.itemCount()));
        form.add("cus_name", s.getFullName());
        form.add("cus_email", order.getEmail());
        form.add("cus_add1", s.getLine1());
        form.add("cus_city", s.getCity());
        form.add("cus_postcode", s.getPostcode() == null || s.getPostcode().isBlank() ? "1000" : s.getPostcode());
        form.add("cus_country", "Bangladesh");
        form.add("cus_phone", s.getPhone());
        form.add("ship_name", s.getFullName());
        form.add("ship_add1", s.getLine1());
        form.add("ship_city", s.getCity());
        form.add("ship_postcode", s.getPostcode() == null || s.getPostcode().isBlank() ? "1000" : s.getPostcode());
        form.add("ship_country", "Bangladesh");
        form.add("value_a", order.getCode());
        try {
            String body = restClient.post()
                    .uri(props.sslcommerz().base() + "/gwprocess/v4/api.php")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            Map<String, Object> res = JsonParserFactory.getJsonParser().parseMap(body);
            if ("SUCCESS".equalsIgnoreCase(str(res.get("status"))) && res.get("GatewayPageURL") != null) {
                return InitResult.ok(str(res.get("GatewayPageURL")), str(res.get("sessionkey")));
            }
            log.warn("SSLCommerz init failed: tran={} reason={}", payment.getTranId(), res.get("failedreason"));
            return InitResult.failed("SSLCommerz could not start the payment: " + str(res.get("failedreason")));
        } catch (Exception e) {
            log.warn("SSLCommerz init error: tran={} {}", payment.getTranId(), e.getMessage());
            return InitResult.failed("Could not reach SSLCommerz. Please try again.");
        }
    }

    /** Validation API: the only answer we trust (status must be VALID or VALIDATED). */
    public Map<String, Object> validate(String valId) {
        String url = UriComponentsBuilder.fromUriString(props.sslcommerz().base() + "/validator/api/validationserverAPI.php")
                .queryParam("val_id", valId)
                .queryParam("store_id", props.sslcommerz().storeId())
                .queryParam("store_passwd", props.sslcommerz().storePass())
                .queryParam("v", "1")
                .queryParam("format", "json")
                .build().encode().toUriString();
        String body = restClient.get().uri(java.net.URI.create(url)).retrieve().body(String.class);
        return JsonParserFactory.getJsonParser().parseMap(body);
    }

    /** Transaction Query API by our tran_id, for reconciliation. First element or an empty map. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> query(String tranId) {
        String url = UriComponentsBuilder.fromUriString(props.sslcommerz().base() + "/validator/api/merchantTransIDvalidationAPI.php")
                .queryParam("tran_id", tranId)
                .queryParam("store_id", props.sslcommerz().storeId())
                .queryParam("store_passwd", props.sslcommerz().storePass())
                .queryParam("format", "json")
                .build().encode().toUriString();
        String body = restClient.get().uri(java.net.URI.create(url)).retrieve().body(String.class);
        Map<String, Object> res = JsonParserFactory.getJsonParser().parseMap(body);
        Object element = res.get("element");
        if (element instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    @Override
    public RefundResult refund(Payment payment, long amountBdt, String reason) {
        if (!available()) return new RefundResult(false, false, null, "SSLCommerz is not configured.");
        if (payment.getBankTranId() == null) return new RefundResult(false, false, null, "Missing bank transaction id.");
        String refundTransId = "RF-" + payment.getTranId();
        String url = UriComponentsBuilder.fromUriString(props.sslcommerz().base() + "/validator/api/merchantTransIDvalidationAPI.php")
                .queryParam("bank_tran_id", payment.getBankTranId())
                .queryParam("refund_trans_id", refundTransId.length() > 30 ? refundTransId.substring(0, 30) : refundTransId)
                .queryParam("refund_amount", amount(amountBdt))
                .queryParam("refund_remarks", reason == null ? "Customer refund" : reason)
                .queryParam("store_id", props.sslcommerz().storeId())
                .queryParam("store_passwd", props.sslcommerz().storePass())
                .queryParam("v", "1")
                .queryParam("format", "json")
                .build().encode().toUriString();
        try {
            String body = restClient.get().uri(java.net.URI.create(url)).retrieve().body(String.class);
            Map<String, Object> res = JsonParserFactory.getJsonParser().parseMap(body);
            String status = str(res.get("status")).toLowerCase(Locale.ROOT);
            if ("success".equals(status) || "processing".equals(status)) {
                return new RefundResult(true, false, str(res.get("refund_ref_id")), null);
            }
            return new RefundResult(false, false, null, str(res.get("errorReason")));
        } catch (Exception e) {
            log.warn("SSLCommerz refund error: tran={} {}", payment.getTranId(), e.getMessage());
            return new RefundResult(false, false, null, "Could not reach SSLCommerz.");
        }
    }

    static String amount(long bdt) {
        return bdt + ".00";
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
