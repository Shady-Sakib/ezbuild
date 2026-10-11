package ezbuild.service;

import ezbuild.model.OrderStatus;
import ezbuild.model.Review;
import ezbuild.model.User;
import ezbuild.repository.OrderRepository;
import ezbuild.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final List<OrderStatus> BOUGHT = List.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED,
            OrderStatus.DELIVERED, OrderStatus.PLACED);

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProductService productService;

    public List<Review> forProduct(String productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    public Review mine(String productId, String userId) {
        return userId == null ? null : reviewRepository.findByProductIdAndUserId(productId, userId).orElse(null);
    }

    /** One review per customer per product (unique index); posting again updates it. */
    public Review save(User user, String productId, Review form) {
        productService.get(productId);
        Review review = reviewRepository.findByProductIdAndUserId(productId, user.getId()).orElseGet(Review::new);
        review.setProductId(productId);
        review.setUserId(user.getId());
        review.setUsername(user.displayLabel());
        review.setRating(Math.max(1, Math.min(5, form.getRating())));
        review.setTitle(form.getTitle().trim());
        review.setComment(form.getComment() == null ? null : form.getComment().trim());
        review.setVerifiedPurchase(orderRepository.existsByUserIdAndItemsProductIdAndStatusIn(user.getId(), productId, BOUGHT));
        if (review.getCreatedAt() == null) review.setCreatedAt(Instant.now());
        Review saved = reviewRepository.save(review);
        recompute(productId);
        return saved;
    }

    public void delete(String reviewId, User user) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new NotFoundException("Review not found."));
        if (!review.getUserId().equals(user.getId()) && !user.isStaff()) throw new NotFoundException("Review not found.");
        reviewRepository.delete(review);
        recompute(review.getProductId());
    }

    private void recompute(String productId) {
        List<Review> all = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
        double avg = all.stream().mapToInt(Review::getRating).average().orElse(0);
        productService.updateRating(productId, Math.round(avg * 10) / 10.0, all.size());
        productService.evict(productId);
    }
}
