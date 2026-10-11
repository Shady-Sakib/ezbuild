package ezbuild.service;

import ezbuild.config.CacheConfig;
import ezbuild.dto.FacetCount;
import ezbuild.dto.Facets;
import ezbuild.dto.PageResult;
import ezbuild.dto.ProductFilter;
import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shop filtering: turns a ProductFilter into a Mongo Criteria and computes sidebar facet counts with
 * aggregations.
 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    /** Spec keys that get a filter group in the sidebar, per category. */
    public static final Map<ProductCategory, List<String>> SPEC_FACETS = Map.ofEntries(
            Map.entry(ProductCategory.CPU, List.of("socket", "cores", "arch", "igpu")),
            Map.entry(ProductCategory.MOTHERBOARD, List.of("socket", "chipset", "ram", "form", "wifi")),
            Map.entry(ProductCategory.RAM, List.of("type", "gb", "mhz")),
            Map.entry(ProductCategory.GPU, List.of("chip", "vram", "upscaler")),
            Map.entry(ProductCategory.STORAGE, List.of("iface", "gb")),
            Map.entry(ProductCategory.PSU, List.of("watt", "eff", "modular")),
            Map.entry(ProductCategory.CASE, List.of("form")),
            Map.entry(ProductCategory.COOLER, List.of("type")),
            Map.entry(ProductCategory.MONITOR, List.of("inch", "res", "hz", "panel")),
            Map.entry(ProductCategory.KEYBOARD, List.of()),
            Map.entry(ProductCategory.MOUSE, List.of()),
            Map.entry(ProductCategory.HEADSET, List.of()));

    private final MongoTemplate mongoTemplate;

    public PageResult<Product> search(ProductFilter f) {
        Criteria criteria = toCriteria(f);
        Query query = Query.query(criteria);
        long total = mongoTemplate.count(query, Product.class);
        query.with(sort(f.sort())).skip((long) f.page() * f.size()).limit(f.size());
        List<Product> content = mongoTemplate.find(query, Product.class);
        return new PageResult<>(content, f.page(), f.size(), total);
    }

    /** Admin listing: same filters, including inactive products. */
    public PageResult<Product> searchAll(ProductFilter f) {
        Criteria criteria = toCriteria(f, false);
        Query query = Query.query(criteria);
        long total = mongoTemplate.count(query, Product.class);
        query.with(Sort.by(Sort.Order.asc("category"), Sort.Order.asc("_id"))).skip((long) f.page() * f.size()).limit(f.size());
        return new PageResult<>(mongoTemplate.find(query, Product.class), f.page(), f.size(), total);
    }

    /** Quick suggestions for the header search box. */
    public List<Product> suggest(String q, int limit) {
        if (q == null || q.trim().length() < 2) return List.of();
        ProductFilter f = new ProductFilter(q, null, null, null, null, false, false, null, "relevance", 0, limit);
        return search(f).content();
    }

    Criteria toCriteria(ProductFilter f) {
        return toCriteria(f, true);
    }

    Criteria toCriteria(ProductFilter f, boolean activeOnly) {
        List<Criteria> and = new ArrayList<>();
        if (activeOnly) and.add(Criteria.where("active").is(true));
        else and.add(Criteria.where("_id").exists(true));
        if (f.category() != null) and.add(Criteria.where("category").is(f.category()));
        if (!f.brands().isEmpty()) and.add(Criteria.where("brand").in(f.brands()));
        if (f.min() != null) and.add(Criteria.where("salePrice").gte(f.min()));
        if (f.max() != null) and.add(Criteria.where("salePrice").lte(f.max()));
        if (f.inStock()) and.add(Criteria.where("stock").gt(0));
        if (f.sale()) and.add(Criteria.where("discountPercent").gt(0));
        if (f.q() != null && !f.q().isBlank()) {
            // Every word must appear in name, brand or SKU. User text is quoted, never used as a raw regex.
            for (String token : f.q().trim().split("\\s+")) {
                if (token.isBlank()) continue;
                Pattern p = Pattern.compile(Pattern.quote(token), Pattern.CASE_INSENSITIVE);
                and.add(new Criteria().orOperator(Criteria.where("name").regex(p), Criteria.where("brand").regex(p),
                        Criteria.where("_id").regex(p)));
            }
        }
        f.specs().forEach((key, values) -> {
            if (key.matches("^[a-z0-9]{1,12}$") && values != null && !values.isEmpty()) {
                and.add(Criteria.where("specs." + key).in(typed(values)));
            }
        });
        return new Criteria().andOperator(and);
    }

    /** URL values are strings but stored specs may be numbers or booleans, so match every form. */
    private static List<Object> typed(List<String> values) {
        List<Object> out = new ArrayList<>();
        for (String v : values) {
            out.add(v);
            if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) out.add(Boolean.parseBoolean(v));
            try {
                double d = Double.parseDouble(v);
                if (d == Math.rint(d)) {
                    out.add((int) d);
                    out.add((long) d);
                }
                out.add(d);
            } catch (NumberFormatException ignored) {
                // not numeric
            }
        }
        return out;
    }

    private static Sort sort(String sort) {
        return switch (sort) {
            case "price_asc" -> Sort.by(Sort.Order.asc("salePrice"), Sort.Order.asc("_id"));
            case "price_desc" -> Sort.by(Sort.Order.desc("salePrice"), Sort.Order.asc("_id"));
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("_id"));
            case "discount" -> Sort.by(Sort.Order.desc("discountPercent"), Sort.Order.asc("salePrice"));
            default -> Sort.by(Sort.Order.desc("soldCount"), Sort.Order.asc("category"), Sort.Order.asc("_id"));
        };
    }

    /** Sidebar counts for a category (or everything). Cached; any product write clears it. */
    @Cacheable(cacheNames = CacheConfig.FACETS, key = "#category == null ? 'all' : #category.name()")
    public Facets facets(ProductCategory category) {
        Criteria base = category == null
                ? Criteria.where("active").is(true)
                : Criteria.where("active").is(true).and("category").is(category);

        List<FacetCount> brands = group(base, "brand");

        Map<String, List<FacetCount>> specs = new LinkedHashMap<>();
        if (category != null) {
            for (String key : SPEC_FACETS.getOrDefault(category, List.of())) {
                List<FacetCount> counts = group(base, "specs." + key);
                counts.sort(Comparator.comparing((FacetCount fc) -> numericOrMax(fc.value())).thenComparing(FacetCount::value));
                if (!counts.isEmpty()) specs.put(key, counts);
            }
        }

        Aggregation bounds = Aggregation.newAggregation(Aggregation.match(base),
                Aggregation.group().min("salePrice").as("min").max("salePrice").as("max"));
        Document b = mongoTemplate.aggregate(bounds, Product.class, Document.class).getUniqueMappedResult();
        long min = b == null ? 0 : ((Number) b.get("min")).longValue();
        long max = b == null ? 0 : ((Number) b.get("max")).longValue();
        return new Facets(brands, specs, min, max);
    }

    public List<FacetCount> categoryCounts() {
        return group(Criteria.where("active").is(true), "category");
    }

    private List<FacetCount> group(Criteria base, String field) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(base),
                Aggregation.match(Criteria.where(field).exists(true)),
                Aggregation.group(field).count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count"));
        AggregationResults<Document> results = mongoTemplate.aggregate(agg, Product.class, Document.class);
        List<FacetCount> out = new ArrayList<>();
        for (Document d : results.getMappedResults()) {
            Object id = d.get("_id");
            if (id == null) continue;
            String value = id instanceof Double dd && dd == Math.rint(dd) ? String.valueOf(dd.longValue()) : String.valueOf(id);
            out.add(new FacetCount(value, ((Number) d.get("count")).longValue()));
        }
        return out;
    }

    private static double numericOrMax(String v) {
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }
}
