package ezbuild.service;

import ezbuild.model.Product;
import ezbuild.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WishlistService {

    public static final int MAX = 200;

    private final UserService userService;
    private final ProductService productService;

    /** True when the product is now in the wishlist. */
    public boolean toggle(User user, String productId) {
        if (!productService.exists(productId)) throw new NotFoundException("Product not found.");
        boolean added;
        if (user.getWishlist().contains(productId)) {
            user.getWishlist().remove(productId);
            added = false;
        } else {
            if (user.getWishlist().size() >= MAX) throw new BusinessException("Your wishlist is full.");
            user.getWishlist().add(productId);
            added = true;
        }
        userService.save(user);
        return added;
    }

    public List<Product> list(User user) {
        return new ArrayList<>(productService.byIds(user.getWishlist()).values());
    }
}
