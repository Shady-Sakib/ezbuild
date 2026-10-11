package ezbuild.controller;

import ezbuild.dto.Facets;
import ezbuild.dto.PageResult;
import ezbuild.dto.ProductFilter;
import ezbuild.model.Product;
import ezbuild.model.Review;
import ezbuild.model.User;
import ezbuild.service.ProductSearchService;
import ezbuild.service.ProductService;
import ezbuild.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ProductController {

    private final ProductSearchService searchService;
    private final ProductService productService;
    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    /**
     * The shop. First load is server-rendered; the sidebar then re-requests with ?partial=1 and swaps only
     * the results.
     */
    @GetMapping("/products")
    public String products(HttpServletRequest request, Model model) {
        ProductFilter filter = FilterParams.from(request);
        PageResult<Product> page = searchService.search(filter);
        model.addAttribute("filter", filter);
        model.addAttribute("page", page);
        if ("1".equals(request.getParameter("partial"))) {
            return "products :: results";
        }
        Facets facets = searchService.facets(filter.category());
        model.addAttribute("facets", facets);
        model.addAttribute("category", filter.category());
        return "products";
    }

    @GetMapping("/products/{id}")
    public String detail(@PathVariable String id, Authentication authentication, Model model) {
        Product product = productService.get(id);
        if (!product.isActive()) throw new ezbuild.service.NotFoundException("Product not found.");
        User me = currentUser.from(authentication);
        model.addAttribute("product", product);
        model.addAttribute("reviews", reviewService.forProduct(id));
        model.addAttribute("related", productService.related(product, 4));
        model.addAttribute("inWishlist", me != null && me.getWishlist().contains(id));
        Review mine = me == null ? null : reviewService.mine(id, me.getId());
        if (!model.containsAttribute("review")) model.addAttribute("review", mine == null ? new Review() : mine);
        return "product";
    }

    @PostMapping("/products/{id}/reviews")
    public String review(@PathVariable String id, @Valid @ModelAttribute("review") Review review, BindingResult result,
                         Authentication authentication, RedirectAttributes ra) {
        User me = currentUser.require(authentication);
        if (result.hasErrors()) {
            ra.addFlashAttribute("org.springframework.validation.BindingResult.review", result);
            ra.addFlashAttribute("review", review);
            ra.addFlashAttribute("error", "Please fix the review form.");
            return "redirect:/products/" + id + "#reviews";
        }
        reviewService.save(me, id, review);
        ra.addFlashAttribute("success", "Thanks! Your review is posted.");
        return "redirect:/products/" + id + "#reviews";
    }
}
