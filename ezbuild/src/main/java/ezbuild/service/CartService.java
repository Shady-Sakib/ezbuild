package ezbuild.service;

import ezbuild.dto.CartLine;
import ezbuild.dto.CartView;
import ezbuild.model.Cart;
import ezbuild.model.Product;
import ezbuild.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Server-side cart, one per user. Stores ids + quantities; prices and stock are read fresh. */
@Service
@RequiredArgsConstructor
public class CartService {

    public static final int MAX_QTY = 10;
    public static final int MAX_LINES = 40;

    private final CartRepository cartRepository;
    private final ProductService productService;

    public CartView view(String userId) {
        Cart cart = load(userId);
        Map<String, Product> products = productService.byIds(cart.getItems().stream()
                .map(Cart.CartItem::getProductId).toList());
        List<CartLine> lines = new ArrayList<>();
        for (Cart.CartItem item : cart.getItems()) {
            Product p = products.get(item.getProductId());
            if (p != null && p.isActive()) lines.add(CartLine.of(p, item.getQty()));
        }
        return CartView.of(lines);
    }

    public int count(String userId) {
        return cartRepository.findById(userId)
                .map(c -> c.getItems().stream().mapToInt(Cart.CartItem::getQty).sum())
                .orElse(0);
    }

    public CartView add(String userId, String productId, int qty) {
        Product p = productService.findActive(productId);
        if (p == null) throw new NotFoundException("That product is not available.");
        if (!p.inStock()) throw new BusinessException(p.getName() + " is out of stock.");
        Cart cart = load(userId);
        Cart.CartItem line = cart.getItems().stream().filter(i -> i.getProductId().equals(productId)).findFirst()
                .orElse(null);
        int wanted = (line == null ? 0 : line.getQty()) + Math.max(1, qty);
        int capped = Math.min(Math.min(wanted, MAX_QTY), p.getStock());
        if (line == null) {
            if (cart.getItems().size() >= MAX_LINES) throw new BusinessException("Your cart is full.");
            cart.getItems().add(new Cart.CartItem(productId, capped));
        } else {
            line.setQty(capped);
        }
        save(cart);
        return view(userId);
    }

    /** Adds a whole build through the same path as the buttons. */
    public CartView addAll(String userId, Collection<String> productIds) {
        for (String id : productIds) {
            try {
                add(userId, id, 1);
            } catch (BusinessException | NotFoundException ignored) {
                // skip parts that went out of stock since the build was made; the cart shows what was added
            }
        }
        return view(userId);
    }

    public CartView setQty(String userId, String productId, int qty) {
        Cart cart = load(userId);
        if (qty <= 0) {
            cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        } else {
            int capped = Math.min(qty, MAX_QTY);
            cart.getItems().stream().filter(i -> i.getProductId().equals(productId)).findFirst()
                    .ifPresent(i -> i.setQty(capped));
        }
        save(cart);
        return view(userId);
    }

    public CartView remove(String userId, String productId) {
        return setQty(userId, productId, 0);
    }

    public void clear(String userId) {
        cartRepository.deleteById(userId);
    }

    private Cart load(String userId) {
        return cartRepository.findById(userId).orElseGet(() -> {
            Cart c = new Cart();
            c.setId(userId);
            return c;
        });
    }

    private void save(Cart cart) {
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);
    }
}
