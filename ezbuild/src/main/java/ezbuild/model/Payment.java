package ezbuild.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One payment attempt for an order. Never stores card data or secrets. */
@Document("payments")
@Getter
@Setter
public class Payment {

    @Id
    private String id;

    @Indexed
    private String orderId;

    private String orderCode;

    private String userId;

    private PaymentMethod provider;

    /** Our unique transaction id (SSLCommerz tran_id). */
    @Indexed(unique = true)
    private String tranId;

    /** SSLCommerz sessionkey or Stripe checkout session id. */
    @Indexed(sparse = true)
    private String providerRef;

    private String valId;

    private String bankTranId;

    /** Stripe payment intent, needed for refunds. */
    private String paymentIntent;

    private long amountBdt;

    private long chargedAmount;

    private String chargedCurrency;

    private PaymentStatus status = PaymentStatus.INITIATED;

    private String riskLevel;

    private String cardType;

    private String failureReason;

    private String refundRef;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
