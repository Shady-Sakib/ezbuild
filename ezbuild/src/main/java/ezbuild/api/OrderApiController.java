package ezbuild.api;

import ezbuild.controller.CurrentUser;
import ezbuild.dto.CheckoutForm;
import ezbuild.dto.OrderView;
import ezbuild.model.Order;
import ezbuild.model.PaymentMethod;
import ezbuild.model.User;
import ezbuild.order.OrderService;
import ezbuild.payment.PaymentGatewayRegistry;
import ezbuild.payment.PaymentService;
import ezbuild.service.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGatewayRegistry registry;
    private final CurrentUser currentUser;

    /** Places an order from the cart. Digital payments then call POST /api/orders/{code}/pay. */
    @PostMapping
    public ResponseEntity<OrderView> place(@Valid @RequestBody CheckoutForm body, Authentication auth) {
        User me = currentUser.require(auth);
        if (!registry.available(body.getPaymentMethod())) throw new BusinessException("That payment method is not available.");
        Order order = orderService.placeOrder(me, body);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{code}").buildAndExpand(order.getCode()).toUri();
        return ResponseEntity.created(location).body(OrderView.of(order));
    }

    @GetMapping
    public List<OrderView> list(Authentication auth) {
        return orderService.forUser(currentUser.require(auth).getId()).stream().map(OrderView::of).toList();
    }

    @GetMapping("/{code}")
    public OrderView get(@PathVariable String code, Authentication auth) {
        return OrderView.of(orderService.getOwned(code, currentUser.require(auth).getId()));
    }

    @PostMapping("/{code}/cancel")
    public OrderView cancel(@PathVariable String code, Authentication auth) {
        return OrderView.of(orderService.cancelByCustomer(code, currentUser.require(auth)));
    }

    /** Starts or retries a payment; the client opens the returned URL. */
    @PostMapping("/{code}/pay")
    public Map<String, String> pay(@PathVariable String code, Authentication auth) {
        Order order = orderService.getOwned(code, currentUser.require(auth).getId());
        if (order.getPaymentMethod() == PaymentMethod.COD) throw new BusinessException("Cash on delivery orders are paid on arrival.");
        return Map.of("redirectUrl", paymentService.start(order));
    }
}
