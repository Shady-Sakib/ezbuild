package ezbuild.repository;

import ezbuild.model.PaymentEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentEventRepository extends MongoRepository<PaymentEvent, String> { }
