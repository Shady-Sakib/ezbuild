package ezbuild.api;

import ezbuild.dto.DashboardStats;
import ezbuild.dto.OrderView;
import ezbuild.dto.ProductView;
import ezbuild.model.OrderStatus;
import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import ezbuild.order.OrderService;
import ezbuild.payment.PaymentService;
import ezbuild.service.BusinessException;
import ezbuild.service.DashboardService;
import ezbuild.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Admin/staff REST API. Guarded twice: URL rules in SecurityConfig and @PreAuthorize on each method. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final ProductService productService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final DashboardService dashboardService;

    public record DiscountRequest(List<String> ids, String category, String brand,
                                  @Min(0) @Max(90) int percent, LocalDate start, LocalDate end) { }

    public record StatusRequest(@NotNull OrderStatus status, String note) { }

    public record RefundRequest(String reason) { }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ResponseEntity<ProductView> create(@Valid @RequestBody Product body) {
        if (productService.exists(body.getId())) throw new BusinessException("That SKU already exists.");
        Product saved = productService.save(body);
        var location = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/products/{id}")
                .buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(location).body(ProductView.of(saved));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ProductView update(@PathVariable String id, @Valid @RequestBody Product body) {
        Product existing = productService.get(id);
        body.setId(id);
        body.setCreatedAt(existing.getCreatedAt());
        body.setSoldCount(existing.getSoldCount());
        body.setRatingAvg(existing.getRatingAvg());
        body.setRatingCount(existing.getRatingCount());
        return ProductView.of(productService.save(body));
    }

    /** Soft delete: hides the product, old orders keep their snapshot. */
    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        productService.setActive(id, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/products/discount")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public Map<String, Integer> discount(@Valid @RequestBody DiscountRequest body) {
        int n = productService.applyDiscount(body.ids(), ProductCategory.fromSlug(body.category()), body.brand(),
                body.percent(), body.start(), body.end());
        return Map.of("updated", n);
    }

    @PostMapping("/orders/{code}/status")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public OrderView status(@PathVariable String code, @Valid @RequestBody StatusRequest body, Authentication auth) {
        return OrderView.of(orderService.changeStatus(code, body.status(), auth.getName(), body.note()));
    }

    @PostMapping("/orders/{code}/refund")
    @PreAuthorize("hasAuthority('ORDER_REFUND')")
    public OrderView refund(@PathVariable String code, @RequestBody(required = false) RefundRequest body,
                            Authentication auth) {
        return OrderView.of(paymentService.refund(code, auth.getName(), body == null ? null : body.reason()));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    public DashboardStats stats() {
        return dashboardService.stats();
    }
}
