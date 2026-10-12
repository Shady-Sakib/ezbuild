package ezbuild.controller;

import ezbuild.model.Payment;
import ezbuild.model.PaymentStatus;
import ezbuild.payment.PaymentService;
import ezbuild.service.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * SSLCommerz posts the browser back here from its site. With SameSite=Lax the session cookie isn't sent on
 * that cross-site POST, so these handlers never touch the session: verify with SSLCommerz, then redirect to
 * the order page (a normal navigation where the cookie comes back).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PaymentCallbackController {

    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    /*
     * No flash attributes: they need the session, and a cross-site POST would create a new one and log the
     * customer out. The outcome travels as ?pay=...
     */
    @RequestMapping(value = "/payment/sslcommerz/success", method = {RequestMethod.POST, RequestMethod.GET})
    public String success(@RequestParam Map<String, String> params) {
        try {
            Payment p = paymentService.confirmSslCommerz(params);
            String result = switch (p.getStatus()) {
                case SUCCEEDED -> "success";
                case RISK_HOLD -> "review";
                case INITIATED -> "pending";
                default -> "failed";
            };
            return "redirect:/orders/" + p.getOrderCode() + "?pay=" + result;
        } catch (NotFoundException e) {
            return "redirect:/orders";
        }
    }

    @RequestMapping(value = "/payment/sslcommerz/fail", method = {RequestMethod.POST, RequestMethod.GET})
    public String fail(@RequestParam(name = "tran_id", defaultValue = "") String tranId) {
        return closeAttempt(tranId, false);
    }

    @RequestMapping(value = "/payment/sslcommerz/cancel", method = {RequestMethod.POST, RequestMethod.GET})
    public String cancel(@RequestParam(name = "tran_id", defaultValue = "") String tranId) {
        return closeAttempt(tranId, true);
    }

    private String closeAttempt(String tranId, boolean cancelled) {
        try {
            Payment p = paymentService.failOrCancel(tranId, cancelled);
            return "redirect:/orders/" + p.getOrderCode() + "?pay=" + (cancelled ? "cancelled" : "failed");
        } catch (NotFoundException e) {
            return "redirect:/orders";
        }
    }

    /** Offline demo gateway page (approve/decline buttons, no card fields). */
    @GetMapping("/payment/sandbox/{tranId}")
    public String sandbox(@PathVariable String tranId, Authentication authentication, Model model) {
        var me = currentUser.require(authentication);
        Payment p = paymentService.byTranId(tranId);
        if (!p.getUserId().equals(me.getId())) throw new NotFoundException("Unknown transaction.");
        model.addAttribute("payment", p);
        return "sandbox-pay";
    }

    @PostMapping("/payment/sandbox/{tranId}")
    public String sandboxDecide(@PathVariable String tranId, @RequestParam boolean approve, Authentication authentication,
                                RedirectAttributes ra) {
        var me = currentUser.require(authentication);
        Payment p = paymentService.sandboxDecision(tranId, me.getId(), approve);
        if (p.getStatus() == PaymentStatus.SUCCEEDED) ra.addFlashAttribute("success", "Demo payment approved.");
        else ra.addFlashAttribute("error", "Demo payment declined. You can retry below.");
        return "redirect:/orders/" + p.getOrderCode();
    }
}
