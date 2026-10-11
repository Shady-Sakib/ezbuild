package ezbuild.service;

import ezbuild.config.CacheConfig;
import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import ezbuild.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;

    @Cacheable(cacheNames = CacheConfig.PRODUCTS, key = "#id")
    public Product get(String id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product not found."));
    }

    public Product findActive(String id) {
        Product p = productRepository.findById(id).orElse(null);
        return p != null && p.isActive() ? p : null;
    }

    /** One query for many ids (no N+1), returned in the requested order. Not cached: used at checkout. */
    public Map<String, Product> byIds(Collection<String> ids) {
        Map<String, Product> found = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<String, Product> ordered = new LinkedHashMap<>();
        for (String id : ids) {
            Product p = found.get(id);
            if (p != null) ordered.put(id, p);
        }
        return ordered;
    }

    public List<Product> inCategory(ProductCategory category, boolean inStockOnly) {
        return inStockOnly
                ? productRepository.findByActiveTrueAndCategoryAndStockGreaterThanOrderByPriceAsc(category, 0)
                : productRepository.findByActiveTrueAndCategoryOrderByPriceAsc(category);
    }

    @Cacheable(cacheNames = CacheConfig.FEATURED, key = "'deals'")
    public List<Product> deals() {
        return productRepository.findTop8ByActiveTrueAndDiscountPercentGreaterThanOrderByDiscountPercentDesc(0)
                .stream().filter(Product::onSale).toList();
    }

    @Cacheable(cacheNames = CacheConfig.FEATURED, key = "'popular'")
    public List<Product> popular() {
        return productRepository.findTop8ByActiveTrueOrderBySoldCountDescCreatedAtDesc();
    }

    public List<Product> related(Product product, int limit) {
        Query q = Query.query(Criteria.where("active").is(true).and("category").is(product.getCategory())
                .and("_id").ne(product.getId())
                .and("salePrice").gte(product.getSalePrice() * 7 / 10).lte(product.getSalePrice() * 13 / 10));
        q.limit(limit);
        return mongoTemplate.find(q, Product.class);
    }

    public boolean exists(String id) {
        return productRepository.existsById(id);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCTS, key = "#product.id"),
            @CacheEvict(cacheNames = {CacheConfig.FACETS, CacheConfig.FEATURED}, allEntries = true)
    })
    public Product save(Product product) {
        if (product.getCreatedAt() == null) product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        product.refreshSalePrice();
        log.info("Product saved: sku={} price={} discount={}%", product.getId(), product.getPrice(),
                product.getDiscountPercent());
        return productRepository.save(product);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCTS, key = "#id"),
            @CacheEvict(cacheNames = {CacheConfig.FACETS, CacheConfig.FEATURED}, allEntries = true)
    })
    public void setActive(String id, boolean active) {
        Product p = get(id);
        p.setActive(active);
        p.setUpdatedAt(Instant.now());
        productRepository.save(p);
    }

    /**
     * Bulk discount on selected ids, or a whole category and/or brand. 0 clears it. Returns how many changed.
     */
    @CacheEvict(cacheNames = {CacheConfig.PRODUCTS, CacheConfig.FACETS, CacheConfig.FEATURED}, allEntries = true)
    public int applyDiscount(List<String> ids, ProductCategory category, String brand, int percent,
                             LocalDate start, LocalDate end) {
        if (percent < 0 || percent > 90) throw new BusinessException("Discount must be between 0 and 90%.");
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException("The discount end date is before the start date.");
        }
        Criteria c;
        if (ids != null && !ids.isEmpty()) {
            c = Criteria.where("_id").in(ids);
        } else if (category != null || (brand != null && !brand.isBlank())) {
            List<Criteria> parts = new java.util.ArrayList<>();
            if (category != null) parts.add(Criteria.where("category").is(category));
            if (brand != null && !brand.isBlank()) parts.add(Criteria.where("brand").is(brand));
            c = new Criteria().andOperator(parts);
        } else {
            throw new BusinessException("Select products, a category or a brand first.");
        }
        List<Product> targets = mongoTemplate.find(Query.query(c), Product.class);
        for (Product p : targets) {
            p.setDiscountPercent(percent);
            p.setDiscountStart(percent == 0 ? null : start);
            p.setDiscountEnd(percent == 0 ? null : end);
            p.refreshSalePrice();
            p.setUpdatedAt(Instant.now());
        }
        productRepository.saveAll(targets);
        log.info("Bulk discount {}% applied to {} products", percent, targets.size());
        return targets.size();
    }

    /** Discount windows start/end at midnight: keep salePrice in sync. */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Dhaka")
    @CacheEvict(cacheNames = {CacheConfig.PRODUCTS, CacheConfig.FACETS, CacheConfig.FEATURED}, allEntries = true)
    public void refreshSalePrices() {
        List<Product> withDiscount = mongoTemplate.find(Query.query(Criteria.where("discountPercent").gt(0)),
                Product.class);
        withDiscount.forEach(Product::refreshSalePrice);
        productRepository.saveAll(withDiscount);
    }

    /** Atomic conditional decrement: only succeeds if stock >= qty (no overselling, no locks). */
    public boolean tryReserve(String productId, int qty) {
        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(productId).and("active").is(true).and("stock").gte(qty)),
                new Update().inc("stock", -qty).inc("soldCount", qty),
                Product.class);
        return result.getModifiedCount() == 1;
    }

    public void release(String productId, int qty) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(productId)),
                new Update().inc("stock", qty).inc("soldCount", -qty), Product.class);
    }

    @CacheEvict(cacheNames = CacheConfig.PRODUCTS, key = "#productId")
    public void evict(String productId) {
        // stock changed: drop the cached product
    }

    public void updateRating(String productId, double avg, int count) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(productId)),
                new Update().set("ratingAvg", avg).set("ratingCount", count), Product.class);
    }
}
