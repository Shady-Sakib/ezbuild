package ezbuild.repository;

import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findByActiveTrueAndCategoryAndStockGreaterThanOrderByPriceAsc(ProductCategory category, int stock);

    List<Product> findByActiveTrueAndCategoryOrderByPriceAsc(ProductCategory category);

    List<Product> findTop8ByActiveTrueAndDiscountPercentGreaterThanOrderByDiscountPercentDesc(int discount);

    List<Product> findTop8ByActiveTrueOrderBySoldCountDescCreatedAtDesc();

    long countByActiveTrue();

    @Query(value = "{ 'active': true, 'stock': { $lte: ?0 } }", count = true)
    long countLowStock(int threshold);

    List<Product> findByActiveTrueAndStockLessThanEqualOrderByStockAsc(int threshold, Pageable pageable);
}
