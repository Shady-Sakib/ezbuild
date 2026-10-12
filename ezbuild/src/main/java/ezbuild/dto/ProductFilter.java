package ezbuild.dto;

import ezbuild.model.ProductCategory;

import java.util.List;
import java.util.Map;

/** Everything the shop sidebar / GET /api/products can filter on. */
public record ProductFilter(String q, ProductCategory category, List<String> brands, Long min, Long max,
                            boolean inStock, boolean sale, Map<String, List<String>> specs, String sort,
                            int page, int size) {

    public ProductFilter {
        brands = brands == null ? List.of() : brands.stream().filter(b -> b != null && !b.isBlank()).toList();
        specs = specs == null ? Map.of() : specs;
        if (min != null && min < 0) min = 0L;   // negative prices make no sense: clamp
        if (max != null && max < 0) max = 0L;
        if (min != null && max != null && min > max) { Long t = min; min = max; max = t; }
        if (page < 0) page = 0;
        if (size <= 0 || size > 60) size = 24;
        if (sort == null || sort.isBlank()) sort = "relevance";
    }
}
