package ezbuild.controller.admin;

import ezbuild.payment.PaymentService;
import ezbuild.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final PaymentService paymentService;
    private final ezbuild.repository.ProductRepository productRepository;

    @GetMapping({"/admin", "/admin/"})
    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    public String dashboard(Model model) {
        model.addAttribute("stats", dashboardService.stats());
        model.addAttribute("attention", paymentService.attention());
        model.addAttribute("lowStock", productRepository.findByActiveTrueAndStockLessThanEqualOrderByStockAsc(3,
                PageRequest.of(0, 8)));
        return "admin/dashboard";
    }
}
