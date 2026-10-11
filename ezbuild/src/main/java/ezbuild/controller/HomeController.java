package ezbuild.controller;

import ezbuild.dto.FacetCount;
import ezbuild.model.Order;
import ezbuild.model.Product;
import ezbuild.order.OrderService;
import ezbuild.service.NotFoundException;
import ezbuild.service.ProductSearchService;
import ezbuild.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ProductService productService;
    private final ProductSearchService searchService;
    private final OrderService orderService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("deals", productService.deals());
        model.addAttribute("popular", productService.popular());
        Map<String, Long> counts = searchService.categoryCounts().stream()
                .collect(Collectors.toMap(FacetCount::value, FacetCount::count));
        model.addAttribute("categoryCounts", counts);
        return "index";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    /** Guest order tracking by order code + phone number. */
    @GetMapping("/track")
    public String track(@RequestParam(required = false) String code, @RequestParam(required = false) String phone,
                        Model model) {
        if (code != null && phone != null) {
            try {
                Order order = orderService.track(code, phone);
                model.addAttribute("order", order);
            } catch (NotFoundException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        model.addAttribute("code", code);
        model.addAttribute("phone", phone);
        return "track";
    }

    /** Side-by-side compare of up to 3 products: /compare?ids=gpu-03,gpu-06 */
    @GetMapping("/compare")
    public String compare(@RequestParam(defaultValue = "") String ids, Model model) {
        List<String> list = java.util.Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isBlank())
                .distinct().limit(3).toList();
        List<Product> products = new ArrayList<>(productService.byIds(list).values());
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        products.forEach(p -> keys.addAll(p.getSpecs().keySet()));
        model.addAttribute("products", products);
        model.addAttribute("specKeys", keys);
        return "compare";
    }
}
