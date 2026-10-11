package ezbuild.controller;

import ezbuild.model.ProductCategory;
import ezbuild.model.User;
import ezbuild.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Model data every layout needs: signed-in user, cart badge, category menu, current path. */
@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final CurrentUser currentUser;
    private final CartService cartService;

    @ModelAttribute
    public void common(Authentication authentication, HttpServletRequest request, Model model) {
        User me = currentUser.from(authentication);
        model.addAttribute("me", me);
        model.addAttribute("cartCount", me == null ? 0 : cartService.count(me.getId()));
        model.addAttribute("navCategories", ProductCategory.values());
        model.addAttribute("currentPath", request.getRequestURI());
    }
}
