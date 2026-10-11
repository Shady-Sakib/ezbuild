package ezbuild.controller;

import ezbuild.model.Order;
import ezbuild.model.PaymentMethod;
import ezbuild.model.User;
import ezbuild.order.OrderService;
import ezbuild.payment.PaymentGatewayRegistry;
import ezbuild.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Arrays;

@Controller
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGatewayRegistry registry;
    private final CurrentUser currentUser;

    @GetMapping("/orders")
    public String orders(Authentication authentication, Model model) {
        model.addAttribute("orders", orderService.forUser(currentUser.require(authentication).getId()));
        return "orders";
    }

    @GetMapping("/orders/{code}")
    public String order(@PathVariable String code,
                        @org.springframework.web.bind.annotation.RequestParam(required = false) String pay,
                        Authentication authentication, Model model) {
        model.addAttribute("pay", pay == null ? "" : pay);
        User me = currentUser.require(authentication);
        Order order = orderService.getOwned(code, me.getId());
        model.addAttribute("order", order);
        model.addAttribute("payments", paymentService.forOrder(order));
        model.addAttribute("digitalMethods", Arrays.stream(PaymentMethod.values())
                .filter(m -> m.isDigital() && registry.available(m)).toList());
        return "order";
    }

    @GetMapping("/orders/{code}/invoice")
    public String invoice(@PathVariable String code, Authentication authentication, Model model) {
        User me = currentUser.require(authentication);
        Order order = me.isStaff() ? orderService.getByCode(code) : orderService.getOwned(code, me.getId());
        model.addAttribute("order", order);
        return "invoice";
    }
}
