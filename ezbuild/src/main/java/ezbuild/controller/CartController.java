package ezbuild.controller;

import ezbuild.model.PaymentMethod;
import ezbuild.model.ShippingZone;
import ezbuild.model.User;
import ezbuild.order.OrderService;
import ezbuild.service.CartService;
import ezbuild.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Plain-form fallbacks for cart and wishlist; the JS normally uses the REST API. */
@Controller
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final WishlistService wishlistService;
    private final OrderService orderService;
    private final CurrentUser currentUser;

    @GetMapping("/cart")
    public String cart(Authentication authentication, Model model) {
        User me = currentUser.require(authentication);
        var cart = cartService.view(me.getId());
        model.addAttribute("cart", cart);
        model.addAttribute("quote", orderService.quote(cart, ShippingZone.INSIDE_DHAKA, PaymentMethod.SSLCOMMERZ));
        return "cart";
    }

    @PostMapping("/cart/add")
    public String add(@RequestParam String productId, @RequestParam(defaultValue = "1") int qty,
                      @RequestParam(defaultValue = "/cart") String back, Authentication authentication,
                      RedirectAttributes ra) {
        cartService.add(currentUser.require(authentication).getId(), productId, qty);
        ra.addFlashAttribute("success", "Added to cart.");
        return "redirect:" + safeBack(back);
    }

    @PostMapping("/cart/update")
    public String update(@RequestParam String productId, @RequestParam int qty, Authentication authentication) {
        cartService.setQty(currentUser.require(authentication).getId(), productId, qty);
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String remove(@RequestParam String productId, Authentication authentication) {
        cartService.remove(currentUser.require(authentication).getId(), productId);
        return "redirect:/cart";
    }

    @PostMapping("/cart/clear")
    public String clear(Authentication authentication) {
        cartService.clear(currentUser.require(authentication).getId());
        return "redirect:/cart";
    }

    @GetMapping("/wishlist")
    public String wishlist(Authentication authentication, Model model) {
        model.addAttribute("products", wishlistService.list(currentUser.require(authentication)));
        return "wishlist";
    }

    @PostMapping("/wishlist/toggle")
    public String toggle(@RequestParam String productId, @RequestParam(defaultValue = "/wishlist") String back,
                         Authentication authentication, RedirectAttributes ra) {
        boolean added = wishlistService.toggle(currentUser.require(authentication), productId);
        ra.addFlashAttribute("success", added ? "Saved to your wishlist." : "Removed from your wishlist.");
        return "redirect:" + safeBack(back);
    }

    /** Only same-site relative paths, to avoid open redirects. */
    static String safeBack(String back) {
        return back != null && back.startsWith("/") && !back.startsWith("//") ? back : "/";
    }
}
