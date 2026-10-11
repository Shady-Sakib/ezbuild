package ezbuild.api;

import ezbuild.controller.CurrentUser;
import ezbuild.controller.FilterParams;
import ezbuild.dto.FacetCount;
import ezbuild.dto.Facets;
import ezbuild.dto.PageResult;
import ezbuild.dto.ProductView;
import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import ezbuild.model.Review;
import ezbuild.service.NotFoundException;
import ezbuild.service.ProductSearchService;
import ezbuild.service.ProductService;
import ezbuild.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductApiController {

    private final ProductSearchService searchService;
    private final ProductService productService;
    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    /**
     * Query example:
     * ?category=gpu&brand=MSI&min=&max=&inStock=1&sale=1&q=&sort=price_asc&page=0&size=24&f.vram=8
     */
    @GetMapping("/products")
    public PageResult<ProductView> products(HttpServletRequest request) {
        PageResult<Product> page = searchService.search(FilterParams.from(request));
        return new PageResult<>(page.content().stream().map(ProductView::of).toList(), page.page(), page.size(),
                page.totalElements());
    }

    @GetMapping("/products/suggest")
    public List<ProductView> suggest(@RequestParam(defaultValue = "") String q) {
        return searchService.suggest(q, 6).stream().map(ProductView::of).toList();
    }

    @GetMapping("/products/{id}")
    public ProductView product(@PathVariable String id) {
        Product p = productService.get(id);
        if (!p.isActive()) throw new NotFoundException("Product not found.");
        return ProductView.of(p);
    }

    @GetMapping("/products/{id}/reviews")
    public List<Review> reviews(@PathVariable String id) {
        return reviewService.forProduct(id).stream().peek(r -> r.setUserId(null)).toList();
    }

    @PostMapping("/products/{id}/reviews")
    public ResponseEntity<Review> addReview(@PathVariable String id, @Valid @RequestBody Review body,
                                            Authentication authentication) {
        Review saved = reviewService.save(currentUser.require(authentication), id, body);
        saved.setUserId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        Map<String, Long> counts = new java.util.HashMap<>();
        searchService.categoryCounts().forEach(fc -> counts.put(fc.value(), fc.count()));
        return Arrays.stream(ProductCategory.values()).map(c -> Map.<String, Object>of(
                "id", c.slug(), "label", c.getLabel(), "buildPart", c.isBuildPart(),
                "count", counts.getOrDefault(c.name(), 0L))).toList();
    }

    @GetMapping("/brands")
    public List<FacetCount> brands(@RequestParam(required = false) String category) {
        return searchService.facets(ProductCategory.fromSlug(category)).brands();
    }

    @GetMapping("/facets")
    public Facets facets(@RequestParam(required = false) String category) {
        return searchService.facets(ProductCategory.fromSlug(category));
    }
}
