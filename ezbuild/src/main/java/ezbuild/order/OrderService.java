package ezbuild.order;

import ezbuild.config.AppProperties;
import ezbuild.dto.CartView;
import ezbuild.dto.CheckoutForm;
import ezbuild.dto.Quote;
import ezbuild.model.Address;
import ezbuild.model.Cart;
import ezbuild.model.Order;
import ezbuild.model.OrderStatus;
import ezbuild.model.PaymentMethod;
import ezbuild.model.PaymentStatus;
import ezbuild.model.Product;
import ezbuild.model.ShippingZone;
import ezbuild.model.User;
import ezbuild.repository.CartRepository;
import ezbuild.repository.OrderRepository;
import ezbuild.service.BusinessException;
import ezbuild.service.CartService;
import ezbuild.service.NotFoundException;
import ezbuild.service.OutOfStockException;
import ezbuild.service.ProductService;
import ezbuild.service.UserService;
import ezbuild.util.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checkout and order lifecycle. Total = sum(effective price x qty) + shipping (60 inside / 100 outside Dhaka)
 * + COD fee (50, COD only).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final ProductService productService;
    private final UserService userService;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    public long shippingFee(ShippingZone zone) {
        return zone == ShippingZone.OUTSIDE_DHAKA ? props.shipping().outside() : props.shipping().inside();
    }

    public Quote quote(CartView cart, ShippingZone zone, PaymentMethod method) {
        long shipping = cart.empty() ? 0 : shippingFee(zone == null ? ShippingZone.INSIDE_DHAKA : zone);
        long cod = method == PaymentMethod.COD && !cart.empty() ? props.payment().codFee() : 0;
        return new Quote(cart.subtotal(), cart.savings(), shipping, cod, cart.subtotal() + shipping + cod);
    }

    /**
     * Checkout transaction: re-price from the DB, reserve stock with atomic conditional decrements, insert
     * the order, clear the cart. Any failure undoes it (reserved stock is given back by hand too, which
     * covers transactions-off mode).
     */
    @Transactional(rollbackFor = Exception.class)
    public Order placeOrder(User user, CheckoutForm form) {
        Cart cart = cartRepository.findById(user.getId()).orElse(null);
        if (cart == null || cart.getItems().isEmpty()) throw new BusinessException("Your cart is empty.");

        Map<String, Product> products = productService.byIds(cart.getItems().stream().map(Cart.CartItem::getProductId).toList());
        PaymentMethod method = form.getPaymentMethod();
        Address address = form.getAddress();

        Order order = new Order();
        order.setCode("EZ" + Hashing.code(6));
        order.setUserId(user.getId());
        order.setUsername(user.getUsername());
        order.setEmail(user.getEmail());
        order.setShipping(address);
        order.setShippingZone(address.getZone());
        order.setPaymentMethod(method);
        order.setNote(form.getNote());

        List<String[]> reserved = new ArrayList<>();
        try {
            long subtotal = 0;
            long savings = 0;
            for (Cart.CartItem item : cart.getItems()) {
                Product p = products.get(item.getProductId());
                if (p == null || !p.isActive()) throw new BusinessException("An item in your cart is no longer available.");
                int qty = Math.max(1, Math.min(item.getQty(), CartService.MAX_QTY));
                if (!productService.tryReserve(p.getId(), qty)) throw new OutOfStockException(p.getName());
                reserved.add(new String[]{p.getId(), String.valueOf(qty)});

                Order.OrderItem line = new Order.OrderItem();
                line.setProductId(p.getId());
                line.setName(p.getName());
                line.setBrand(p.getBrand());
                line.setCategory(p.getCategory());
                line.setUnitListPrice(p.getPrice());
                line.setDiscountPercent(p.activeDiscount());
                line.setUnitPrice(p.effectivePrice());
                line.setQty(qty);
                order.getItems().add(line);
                subtotal += line.lineTotal();
                savings += (p.getPrice() - p.effectivePrice()) * qty;
            }
            long shipping = shippingFee(address.getZone());
            long cod = method == PaymentMethod.COD ? props.payment().codFee() : 0;
            order.setSubtotal(subtotal);
            order.setDiscountTotal(savings);
            order.setShippingFee(shipping);
            order.setCodFee(cod);
            order.setTotal(subtotal + shipping + cod);

            if (method == PaymentMethod.SSLCOMMERZ && order.getTotal() > props.sslcommerz().maxAmount()) {
                throw new BusinessException("SSLCommerz accepts up to ৳" + props.sslcommerz().maxAmount()
                        + " per payment. Choose Stripe or cash on delivery, or split the order.");
            }

            if (method == PaymentMethod.COD) {
                order.setStatus(OrderStatus.PLACED);
                order.setPaymentStatus(PaymentStatus.COD_PENDING);
            } else {
                order.setStatus(OrderStatus.PENDING_PAYMENT);
                order.setPaymentStatus(PaymentStatus.UNPAID);
                order.setExpiresAt(Instant.now().plus(Duration.ofMinutes(props.payment().reservationMinutes())));
            }
            order.getHistory().add(new Order.StatusChange(order.getStatus(), user.getUsername(), "Order placed"));
            order.setCreatedAt(Instant.now());
            Order saved = orderRepository.save(order);
            cartRepository.deleteById(user.getId());
            if (form.isSaveAddress()) userService.saveAddress(user, address);
            reserved.forEach(r -> productService.evict(r[0]));
            events.publishEvent(new OrderEvents.Placed(saved.getId()));
            log.info("Order placed: code={} user={} total={} method={}", saved.getCode(), user.getUsername(),
                    saved.getTotal(), method);
            return saved;
        } catch (RuntimeException e) {
            // transactions on: rollback undoes the decrements; off: give the stock back here
            if (!props.mongoTransactions()) {
                reserved.forEach(r -> productService.release(r[0], Integer.parseInt(r[1])));
            }
            throw e;
        }
    }

    public Order save(Order order) {
        return orderRepository.save(order);
    }

    public List<Order> forUser(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Another user's order code is a 404. */
    public Order getOwned(String code, String userId) {
        return orderRepository.findByCodeAndUserId(code, userId).orElseThrow(() -> new NotFoundException("Order not found."));
    }

    public Order getByCode(String code) {
        return orderRepository.findByCode(code).orElseThrow(() -> new NotFoundException("Order not found."));
    }

    public Order track(String code, String phone) {
        String normalized = phone == null ? "" : phone.replaceAll("[^0-9+]", "");
        return orderRepository.findByCodeAndShippingPhone(code == null ? "" : code.trim().toUpperCase(), normalized)
                .orElseThrow(() -> new NotFoundException("No order matches that code and phone number."));
    }

    public Page<Order> adminList(OrderStatus status, String q, Pageable pageable) {
        if (q != null && !q.isBlank()) return orderRepository.findByCodeContainingIgnoreCase(q.trim(), pageable);
        if (status != null) return orderRepository.findByStatus(status, pageable);
        return orderRepository.findAll(pageable);
    }

    /** Staff/admin status change, checked against the transition table and logged. */
    @Transactional(rollbackFor = Exception.class)
    public Order changeStatus(String code, OrderStatus target, String actor, String note) {
        Order order = getByCode(code);
        applyStatus(order, target, actor, note);
        return orderRepository.save(order);
    }

    /** Customers can cancel until the order is processed. */
    @Transactional(rollbackFor = Exception.class)
    public Order cancelByCustomer(String code, User user) {
        Order order = getOwned(code, user.getId());
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PLACED) {
            throw new BusinessException("This order can no longer be cancelled online. Contact support.");
        }
        applyStatus(order, OrderStatus.CANCELLED, user.getUsername(), "Cancelled by customer");
        if (order.getPaymentStatus() == PaymentStatus.UNPAID || order.getPaymentStatus() == PaymentStatus.COD_PENDING
                || order.getPaymentStatus() == PaymentStatus.INITIATED) {
            order.setPaymentStatus(PaymentStatus.CANCELLED);
        }
        return orderRepository.save(order);
    }

    /** Called by PaymentService inside its confirmation transaction. */
    public void markPaid(Order order, String actor, String note) {
        if (order.getStatus() == OrderStatus.PAID) return;
        if (order.getStatus() == OrderStatus.EXPIRED || order.getStatus() == OrderStatus.CANCELLED) {
            // paid after the reservation expired: try to take the stock again, otherwise flag for refund
            boolean ok = true;
            List<Order.OrderItem> taken = new ArrayList<>();
            for (Order.OrderItem i : order.getItems()) {
                if (productService.tryReserve(i.getProductId(), i.getQty())) taken.add(i);
                else {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                taken.forEach(i -> productService.release(i.getProductId(), i.getQty()));
                order.setNeedsAttention(true);
                order.getHistory().add(new Order.StatusChange(order.getStatus(), actor,
                        "Payment arrived after expiry and stock is gone: refund needed"));
                order.setPaymentStatus(PaymentStatus.SUCCEEDED);
                orderRepository.save(order);
                return;
            }
            order.getHistory().add(new Order.StatusChange(OrderStatus.PAID, actor, "Stock re-reserved after late payment"));
            order.setStatus(OrderStatus.PAID);
        } else {
            applyStatus(order, OrderStatus.PAID, actor, note);
        }
        order.setPaymentStatus(PaymentStatus.SUCCEEDED);
        order.setExpiresAt(null);
        orderRepository.save(order);
        events.publishEvent(new OrderEvents.Paid(order.getId()));
    }

    /** Every minute: unpaid digital orders past their reservation give their stock back. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void expireUnpaid() {
        List<Order> due = orderRepository.findByStatusAndExpiresAtBefore(OrderStatus.PENDING_PAYMENT, Instant.now());
        for (Order order : due) {
            try {
                applyStatus(order, OrderStatus.EXPIRED, "system", "Payment not completed in time");
                order.setPaymentStatus(PaymentStatus.CANCELLED);
                orderRepository.save(order);
                log.info("Order expired and stock released: code={}", order.getCode());
            } catch (RuntimeException e) {
                log.warn("Could not expire order {}: {}", order.getCode(), e.getMessage());
            }
        }
    }

    void applyStatus(Order order, OrderStatus target, String actor, String note) {
        OrderStatus from = order.getStatus();
        if (from == target) return;
        if (!from.canTransitionTo(target)) {
            throw new BusinessException("An order cannot go from " + from.getLabel() + " to " + target.getLabel() + ".");
        }
        if (target == OrderStatus.CANCELLED || target == OrderStatus.EXPIRED || target == OrderStatus.REFUNDED) {
            for (Order.OrderItem i : order.getItems()) {
                productService.release(i.getProductId(), i.getQty());
                productService.evict(i.getProductId());
            }
        }
        if (target == OrderStatus.DELIVERED && order.getPaymentMethod() == PaymentMethod.COD) {
            order.setPaymentStatus(PaymentStatus.SUCCEEDED);
        }
        order.setStatus(target);
        order.getHistory().add(new Order.StatusChange(target, actor, note));
        events.publishEvent(new OrderEvents.StatusChanged(order.getId(), target.name()));
    }
}
