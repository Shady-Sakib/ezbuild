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

    /**
     * Side-by-side compare of up to 3 products from ONE category: /compare?ids=gpu-03,gpu-06.
     * The first product fixes the category; "add" appends a pick from the dropdown, "category" starts empty.
     */
    @GetMapping("/compare")
    public String compare(@RequestParam(defaultValue = "") String ids, @RequestParam(required = false) String add,
                          @RequestParam(required = false) String category, Model model) {
        java.util.LinkedHashSet<String> wanted = new java.util.LinkedHashSet<>();
        for (String id : ids.split(",")) if (!id.isBlank()) wanted.add(id.trim());
        if (add != null && !add.isBlank()) wanted.add(add.trim());
        Map<String, Product> found = productService.byIds(new ArrayList<>(wanted));
        List<Product> products = new ArrayList<>();
        ezbuild.model.ProductCategory cat = products.isEmpty() && category != null
                ? ezbuild.model.ProductCategory.fromSlug(category) : null;
        for (String id : wanted) {
            Product p = found.get(id);
            if (p == null) continue;
            if (cat == null) cat = p.getCategory();
            if (p.getCategory() == cat && products.size() < 3) products.add(p);
        }
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        products.forEach(p -> keys.addAll(p.getSpecs().keySet()));
        java.util.Set<String> differing = new java.util.HashSet<>();
        for (String k : keys) {
            if (products.stream().map(p -> String.valueOf(p.getSpecs().get(k))).distinct().count() > 1) differing.add(k);
        }
        List<Product> candidates = List.of();
        if (cat != null && products.size() < 3) {
            java.util.Set<String> chosen = products.stream().map(Product::getId).collect(Collectors.toSet());
            candidates = searchService.search(new ezbuild.dto.ProductFilter(null, cat, null, null, null, false, false,
                            null, "price_asc", 0, 60)).content().stream()
                    .filter(p -> !chosen.contains(p.getId())).toList();
        }
        model.addAttribute("products", products);
        model.addAttribute("specKeys", keys);
        model.addAttribute("differing", differing);
        model.addAttribute("candidates", candidates);
        model.addAttribute("compareCategory", cat);
        model.addAttribute("idsCsv", products.stream().map(Product::getId).collect(Collectors.joining(",")));
        Map<String, String> removeLinks = new java.util.LinkedHashMap<>();
        for (Product p : products) {
            String rest = products.stream().map(Product::getId).filter(x -> !x.equals(p.getId()))
                    .collect(Collectors.joining(","));
            removeLinks.put(p.getId(), rest.isEmpty() ? "/compare?category=" + p.getCategory().slug() : "/compare?ids=" + rest);
        }
        model.addAttribute("removeLinks", removeLinks);
        model.addAttribute("categories", ezbuild.model.ProductCategory.values());
        return "compare";
    }
}
