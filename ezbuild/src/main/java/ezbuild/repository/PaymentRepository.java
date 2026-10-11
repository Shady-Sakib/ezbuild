package ezbuild.repository;

import ezbuild.model.Payment;
import ezbuild.model.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByTranId(String tranId);

    Optional<Payment> findByProviderRef(String providerRef);

    List<Payment> findByOrderIdOrderByCreatedAtDesc(String orderId);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant cutoff);

    List<Payment> findByStatusIn(List<PaymentStatus> statuses);
}
