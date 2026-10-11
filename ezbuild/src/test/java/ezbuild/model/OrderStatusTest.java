package ezbuild.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void allowedTransitions() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.PROCESSING)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void forbiddenTransitions() {
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.PENDING_PAYMENT)).isFalse();
        assertThat(OrderStatus.EXPIRED.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.CANCELLED.next()).isEmpty();
    }

    @Test
    void discountMathIsInOnePlace() {
        Product p = new Product();
        p.setPrice(55_500);
        p.setDiscountPercent(10);
        assertThat(p.effectivePrice()).isEqualTo(49_950);
        p.setDiscountEnd(java.time.LocalDate.now().minusDays(1));
        assertThat(p.effectivePrice()).isEqualTo(55_500);
    }
}
