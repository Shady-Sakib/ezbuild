package ezbuild.controller.admin;

import ezbuild.controller.FilterParams;
import ezbuild.dto.ProductFilter;
import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import ezbuild.service.BusinessException;
import ezbuild.service.FileStorageService;
import ezbuild.service.ProductSearchService;
import ezbuild.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Catalogue admin (PRODUCT_WRITE): CRUD, image upload named by SKU, single and bulk discounts. */
@Controller
@RequestMapping("/admin/products")
@PreAuthorize("hasAuthority('PRODUCT_WRITE')")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final ProductSearchService searchService;
    private final FileStorageService fileStorageService;

    @ModelAttribute("categories")
    public ProductCategory[] categories() {
        return ProductCategory.values();
    }

    @GetMapping
    public String list(HttpServletRequest request, Model model) {
        ProductFilter f = FilterParams.from(request);
        ProductFilter all = new ProductFilter(f.q(), f.category(), f.brands(), null, null, false, f.sale(), Map.of(),
                f.sort(), f.page(), 40);
        model.addAttribute("page", adminSearch(all));
        model.addAttribute("filter", all);
        model.addAttribute("brands", searchService.facets(null).brands());
        return "admin/products";
    }

    /** Admin also sees inactive products. */
    private ezbuild.dto.PageResult<Product> adminSearch(ProductFilter f) {
        return searchService.searchAll(f);
    }

    @GetMapping("/new")
    public String create(Model model) {
        Product p = new Product();
        p.setCategory(ProductCategory.CPU);
        p.setStock(10);
        model.addAttribute("product", p);
        model.addAttribute("isNew", true);
        model.addAttribute("specsText", "");
        return "admin/product-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String id, Model model) {
        Product p = productService.get(id);
        model.addAttribute("product", p);
        model.addAttribute("isNew", false);
        model.addAttribute("specsText", specsToText(p.getSpecs()));
        return "admin/product-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("product") Product form, BindingResult result,
                       @RequestParam(defaultValue = "false") boolean isNew,
                       @RequestParam(defaultValue = "") String specsText,
                       @RequestParam(required = false) MultipartFile image,
                       Authentication authentication, Model model, RedirectAttributes ra) {
        if (isNew && productService.exists(form.getId())) {
            result.rejectValue("id", "taken", "That SKU already exists.");
        }
        if (result.hasErrors()) {
            model.addAttribute("isNew", isNew);
            model.addAttribute("specsText", specsText);
            return "admin/product-form";
        }
        Product target = isNew ? new Product() : productService.get(form.getId());
        target.setId(form.getId());
        target.setName(form.getName().trim());
        target.setBrand(form.getBrand().trim());
        target.setCategory(form.getCategory());
        target.setPrice(form.getPrice());
        target.setDiscountPercent(form.getDiscountPercent());
        target.setDiscountStart(form.getDiscountStart());
        target.setDiscountEnd(form.getDiscountEnd());
        target.setStock(form.getStock());
        target.setActive(form.isActive());
        target.setSummary(form.getSummary());
        target.setDescription(form.getDescription());
        target.setPerfScore(form.getPerfScore());
        target.setSpecs(textToSpecs(specsText));
        if (isNew) target.setPriceStatus("manual");
        productService.save(target);
        try {
            fileStorageService.saveProductImage(target.getId(), image);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (java.io.IOException e) {
            ra.addFlashAttribute("error", "The image could not be saved.");
        }
        ra.addFlashAttribute("success", "Saved " + target.getId() + " by " + authentication.getName() + ".");
        return "redirect:/admin/products";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable String id, RedirectAttributes ra) {
        Product p = productService.get(id);
        productService.setActive(id, !p.isActive());
        ra.addFlashAttribute("success", p.getName() + (p.isActive() ? " hidden from the shop." : " is live again."));
        return "redirect:/admin/products";
    }

    /** Bulk discount: selected rows, or a whole category and/or brand. 0 clears it. */
    @PostMapping("/discount")
    public String discount(@RequestParam(required = false) List<String> ids,
                           @RequestParam(required = false) String category,
                           @RequestParam(required = false) String brand,
                           @RequestParam int percent,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                           RedirectAttributes ra) {
        try {
            int n = productService.applyDiscount(ids, ProductCategory.fromSlug(category), brand, percent, start, end);
            ra.addFlashAttribute("success", percent == 0 ? "Discount cleared on " + n + " products."
                    : percent + "% discount applied to " + n + " products.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/products";
    }

    static String specsToText(Map<String, Object> specs) {
        StringBuilder sb = new StringBuilder();
        specs.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sb.toString();
    }

    static Map<String, Object> textToSpecs(String text) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String k = line.substring(0, eq).trim().toLowerCase();
            String v = line.substring(eq + 1).trim();
            if (!k.matches("^[a-z0-9]{1,12}$") || v.isEmpty()) continue;
            if (v.equals("true") || v.equals("false")) out.put(k, Boolean.parseBoolean(v));
            else if (v.matches("-?\\d+")) out.put(k, Integer.parseInt(v));
            else out.put(k, v);
        }
        return out;
    }
}
