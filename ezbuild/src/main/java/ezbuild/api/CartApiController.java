package ezbuild.api;

import ezbuild.controller.CurrentUser;
import ezbuild.dto.CartView;
import ezbuild.service.CartService;
import ezbuild.service.WishlistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CartApiController {

    private final CartService cartService;
    private final WishlistService wishlistService;
    private final CurrentUser currentUser;

    public record AddItem(@NotBlank String productId, @Min(1) @Max(10) Integer qty) { }

    public record SetQty(@Min(0) @Max(10) int qty) { }

    public record AddMany(@NotEmpty @Size(max = 20) List<String> productIds) { }

    @GetMapping("/cart")
    public CartView cart(Authentication auth) {
        return cartService.view(currentUser.require(auth).getId());
    }

    @PostMapping("/cart/items")
    public CartView add(@Valid @RequestBody AddItem body, Authentication auth) {
        return cartService.add(currentUser.require(auth).getId(), body.productId(), body.qty() == null ? 1 : body.qty());
    }

    /** Adds every part of a build (AI plan or saved build) in one go. */
    @PostMapping("/cart/build")
    public CartView addBuild(@Valid @RequestBody AddMany body, Authentication auth) {
        return cartService.addAll(currentUser.require(auth).getId(), body.productIds());
    }

    @PatchMapping("/cart/items/{productId}")
    public CartView setQty(@PathVariable String productId, @Valid @RequestBody SetQty body, Authentication auth) {
        return cartService.setQty(currentUser.require(auth).getId(), productId, body.qty());
    }

    @DeleteMapping("/cart/items/{productId}")
    public CartView remove(@PathVariable String productId, Authentication auth) {
        return cartService.remove(currentUser.require(auth).getId(), productId);
    }

    @DeleteMapping("/cart")
    public ResponseEntity<Void> clear(Authentication auth) {
        cartService.clear(currentUser.require(auth).getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/wishlist/{productId}")
    public Map<String, Object> toggleWishlist(@PathVariable String productId, Authentication auth) {
        boolean added = wishlistService.toggle(currentUser.require(auth), productId);
        return Map.of("productId", productId, "inWishlist", added);
    }
}
