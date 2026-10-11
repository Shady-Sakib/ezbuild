package ezbuild.build;

import ezbuild.model.Product;
import ezbuild.model.ProductCategory;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** In-stock active products grouped by category and sorted by price: the planner's snapshot. */
public record Catalog(Map<ProductCategory, List<Product>> byCategory) {

    public static Catalog of(List<Product> products) {
        Map<ProductCategory, List<Product>> map = new EnumMap<>(ProductCategory.class);
        for (ProductCategory c : ProductCategory.values()) {
            map.put(c, products.stream()
                    .filter(p -> p.getCategory() == c && p.isActive() && p.inStock())
                    .sorted(Comparator.comparingLong(Product::effectivePrice).thenComparing(Product::getId))
                    .toList());
        }
        return new Catalog(map);
    }

    public List<Product> get(ProductCategory category) {
        return byCategory.getOrDefault(category, List.of());
    }
}
