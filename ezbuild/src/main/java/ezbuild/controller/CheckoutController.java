package ezbuild.controller;

import ezbuild.config.AppProperties;
import ezbuild.dto.CartView;
import ezbuild.dto.CheckoutForm;
import ezbuild.model.Order;
import ezbuild.model.PaymentMethod;
import ezbuild.model.ShippingZone;
import ezbuild.model.User;
import ezbuild.order.OrderService;
import ezbuild.payment.PaymentGatewayRegistry;
import ezbuild.payment.PaymentService;
import ezbuild.service.BusinessException;
import ezbuild.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class CheckoutController {

    private final CartService cartService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGatewayRegistry registry;
    private final CurrentUser currentUser;
    private final AppProperties props;

    @ModelAttribute("paymentMethods")
    public List<PaymentMethod> paymentMethods() {
        return Arrays.stream(PaymentMethod.values()).filter(registry::available).toList();
    }

    /** Gateways with missing keys: shown greyed out so it's obvious why they can't be chosen. */
    @ModelAttribute("unconfiguredMethods")
    public List<PaymentMethod> unconfiguredMethods() {
        return Arrays.stream(PaymentMethod.values())
                .filter(m -> m == PaymentMethod.SSLCOMMERZ || m == PaymentMethod.STRIPE)
                .filter(m -> !registry.available(m)).toList();
    }

    @ModelAttribute("zones")
    public ShippingZone[] zones() {
        return ShippingZone.values();
    }

    @GetMapping("/checkout")
    public String checkout(Authentication authentication, Model model) {
        User me = currentUser.require(authentication);
        CartView cart = cartService.view(me.getId());
        if (cart.empty()) return "redirect:/cart";
        CheckoutForm form = (CheckoutForm) model.getAttribute("form");
        if (form == null) {
            form = new CheckoutForm();
            if (me.defaultAddress() != null) form.setAddress(me.defaultAddress());
            else {
                form.getAddress().setFullName(me.displayLabel());
                form.getAddress().setPhone(me.getProfile().getPhone());
            }
            model.addAttribute("form", form);
        }
        fill(model, cart, form);
        return "checkout";
    }

    @PostMapping("/checkout")
    public String place(@Valid @ModelAttribute("form") CheckoutForm form, BindingResult result, Authentication authentication,
                        Model model, RedirectAttributes ra) {
        User me = currentUser.require(authentication);
        CartView cart = cartService.view(me.getId());
        if (cart.empty()) return "redirect:/cart";
        if (form.getPaymentMethod() != null && !registry.available(form.getPaymentMethod())) {
            result.rejectValue("paymentMethod", "unavailable", "That payment method is not available right now.");
        }
        if (result.hasErrors()) {
            fill(model, cart, form);
            return "checkout";
        }
        Order order;
        try {
            order = orderService.placeOrder(me, form);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            fill(model, cartService.view(me.getId()), form);
            return "checkout";
        }
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            ra.addFlashAttribute("success", "Order placed! Pay in cash when your parts arrive.");
            return "redirect:/orders/" + order.getCode();
        }
        try {
            return "redirect:" + paymentService.start(order);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage() + " Your order is saved; you can retry payment below.");
            return "redirect:/orders/" + order.getCode();
        }
    }

    /** Retry payment for an order that is still waiting (after a failed or cancelled attempt). */
    @PostMapping("/orders/{code}/pay")
    public String pay(@PathVariable String code, @RequestParam(required = false) PaymentMethod method,
                      Authentication authentication, RedirectAttributes ra) {
        User me = currentUser.require(authentication);
        Order order = orderService.getOwned(code, me.getId());
        if (method != null && method.isDigital() && registry.available(method) && order.payable()) {
            order.setPaymentMethod(method);
            orderService.save(order);
        }
        try {
            return "redirect:" + paymentService.start(order);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders/" + code;
        }
    }

    @PostMapping("/orders/{code}/cancel")
    public String cancel(@PathVariable String code, Authentication authentication, RedirectAttributes ra) {
        orderService.cancelByCustomer(code, currentUser.require(authentication));
        ra.addFlashAttribute("success", "Order cancelled.");
        return "redirect:/orders/" + code;
    }

    /** Stripe success page: shows status only, after asking Stripe directly. Never trusts the URL. */
    @GetMapping("/checkout/success")
    public String stripeSuccess(@RequestParam("session_id") String sessionId, Authentication authentication,
                                RedirectAttributes ra) {
        currentUser.require(authentication);
        var payment = paymentService.confirmStripeSession(sessionId);
        if (payment.isEmpty()) return "redirect:/orders";
        ra.addFlashAttribute("success", "Thanks! We're confirming your payment with Stripe.");
        return "redirect:/orders/" + payment.get().getOrderCode();
    }

    @GetMapping("/checkout/cancelled")
    public String stripeCancelled(@RequestParam(required = false) String order, RedirectAttributes ra) {
        ra.addFlashAttribute("error", "Payment was cancelled. Your order is kept for "
                + props.payment().reservationMinutes() + " minutes so you can retry.");
        return order == null ? "redirect:/orders" : "redirect:/orders/" + order;
    }

    private void fill(Model model, CartView cart, CheckoutForm form) {
        model.addAttribute("cart", cart);
        model.addAttribute("quote", orderService.quote(cart, form.getAddress().getZone(), form.getPaymentMethod()));
        model.addAttribute("insideFee", props.shipping().inside());
        model.addAttribute("outsideFee", props.shipping().outside());
        model.addAttribute("codFee", props.payment().codFee());
        model.addAttribute("sslMax", props.sslcommerz().maxAmount());
    }
}
