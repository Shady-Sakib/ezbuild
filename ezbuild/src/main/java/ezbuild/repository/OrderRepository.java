package ezbuild.repository;

import ezbuild.model.Order;
import ezbuild.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<Order> findByCode(String code);

    Optional<Order> findByCodeAndUserId(String code, String userId);

    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, Instant cutoff);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByCodeContainingIgnoreCase(String code, Pageable pageable);

    long countByStatusIn(List<OrderStatus> statuses);

    boolean existsByUserIdAndItemsProductIdAndStatusIn(String userId, String productId, List<OrderStatus> statuses);

    Optional<Order> findByCodeAndShippingPhone(String code, String phone);
}
