package ezbuild.controller.admin;

import ezbuild.model.Order;
import ezbuild.model.OrderStatus;
import ezbuild.order.OrderService;
import ezbuild.payment.PaymentService;
import ezbuild.service.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public String list(@RequestParam(required = false) OrderStatus status, @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("orders", orderService.adminList(status, q,
                PageRequest.of(Math.max(page, 0), 25, Sort.by(Sort.Direction.DESC, "createdAt"))));
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("status", status);
        model.addAttribute("q", q);
        return "admin/orders";
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public String detail(@PathVariable String code, Model model) {
        Order order = orderService.getByCode(code);
        model.addAttribute("order", order);
        model.addAttribute("payments", paymentService.forOrder(order));
        return "admin/order";
    }

    @PostMapping("/{code}/status")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public String status(@PathVariable String code, @RequestParam OrderStatus status,
                         @RequestParam(required = false) String note, Authentication authentication, RedirectAttributes ra) {
        try {
            orderService.changeStatus(code, status, authentication.getName(), note);
            ra.addFlashAttribute("success", "Order " + code + " is now " + status.getLabel() + ".");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/orders/" + code;
    }

    @PostMapping("/{code}/refund")
    @PreAuthorize("hasAuthority('ORDER_REFUND')")
    public String refund(@PathVariable String code, @RequestParam(required = false) String reason,
                         Authentication authentication, RedirectAttributes ra) {
        try {
            paymentService.refund(code, authentication.getName(), reason);
            ra.addFlashAttribute("success", "Refund started for " + code + ".");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/orders/" + code;
    }
}
