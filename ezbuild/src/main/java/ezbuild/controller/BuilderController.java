package ezbuild.controller;

import ezbuild.model.Build;
import ezbuild.model.BuildSource;
import ezbuild.model.ProductCategory;
import ezbuild.model.User;
import ezbuild.service.BuildService;
import ezbuild.service.CartService;
import ezbuild.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Manual PC builder with live compatibility checks, saved builds and share links. */
@Controller
@RequiredArgsConstructor
public class BuilderController {

    private final BuildService buildService;
    private final ProductService productService;
    private final CartService cartService;
    private final CurrentUser currentUser;

    @GetMapping("/builder")
    public String builder(@RequestParam(required = false) String ids, Model model) {
        Map<ProductCategory, Object> options = new EnumMap<>(ProductCategory.class);
        for (ProductCategory c : ProductCategory.buildParts()) {
            options.put(c, productService.inCategory(c, false));
        }
        List<String> selected = ids == null ? List.of() : Arrays.stream(ids.split(",")).map(String::trim)
                .filter(s -> !s.isBlank()).toList();
        model.addAttribute("options", options);
        model.addAttribute("selected", selected);
        model.addAttribute("plan", buildService.validate(selected));
        return "builder";
    }

    @GetMapping("/builds")
    public String builds(Authentication authentication, Model model) {
        model.addAttribute("builds", buildService.list(currentUser.require(authentication).getId()));
        return "builds";
    }

    @PostMapping("/builds")
    public String save(@RequestParam(defaultValue = "") String ids, @RequestParam(required = false) String name,
                       @RequestParam(defaultValue = "MANUAL") BuildSource source, Authentication authentication,
                       RedirectAttributes ra) {
        User me = currentUser.require(authentication);
        List<String> list = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        Build build = buildService.save(me.getId(), name, source, list, null);
        ra.addFlashAttribute("success", "Build saved. Share link: /b/" + build.getShareCode());
        return "redirect:/builds";
    }

    @PostMapping("/builds/{id}/delete")
    public String delete(@PathVariable String id, Authentication authentication, RedirectAttributes ra) {
        buildService.delete(id, currentUser.require(authentication).getId());
        ra.addFlashAttribute("success", "Build deleted.");
        return "redirect:/builds";
    }

    @PostMapping("/builds/{id}/cart")
    public String toCart(@PathVariable String id, Authentication authentication, RedirectAttributes ra) {
        User me = currentUser.require(authentication);
        Build build = buildService.getOwned(id, me.getId());
        cartService.addAll(me.getId(), buildService.productIds(build));
        ra.addFlashAttribute("success", "Build added to your cart.");
        return "redirect:/cart";
    }

    /** Public share link: /b/xk29ab opens the build read-only in the builder. */
    @GetMapping("/b/{code}")
    public String shared(@PathVariable String code) {
        Build build = buildService.getShared(code);
        return "redirect:/builder?ids=" + String.join(",", buildService.productIds(build));
    }
}
