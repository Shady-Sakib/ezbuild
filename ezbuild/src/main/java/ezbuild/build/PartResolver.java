package ezbuild.build;

import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps phrases like "ryzen 5 7600" or "rtx 5060 ti" to catalogue products. Model numbers must match exactly;
 * one clear winner is locked, several close ones become a question.
 */
@Component
public class PartResolver {

    public record Resolution(Map<ProductCategory, Product> locked, List<String> unresolved,
                             Map<String, List<Product>> ambiguous) { }

    public Resolution resolve(List<String> phrases, List<Product> catalogue) {
        Map<ProductCategory, Product> locked = new EnumMap<>(ProductCategory.class);
        List<String> unresolved = new ArrayList<>();
        Map<String, List<Product>> ambiguous = new java.util.LinkedHashMap<>();
        for (String phrase : phrases) {
            List<Scored> scored = catalogue.stream()
                    .map(p -> new Scored(p, score(phrase, p)))
                    .filter(s -> s.score > 0)
                    .sorted(Comparator.comparingDouble(Scored::score).reversed()
                            .thenComparing(s -> s.product.effectivePrice()))
                    .toList();
            if (scored.isEmpty()) {
                unresolved.add(phrase);
                continue;
            }
            double top = scored.get(0).score;
            List<Product> close = scored.stream().filter(s -> s.score >= top - 0.01).map(Scored::product).toList();
            // several cards share a model (e.g. three RTX 5060s): take the cheapest in stock
            boolean sameModel = close.stream().map(p -> p.getCategory()).distinct().count() == 1;
            if (close.size() == 1 || sameModel) {
                Product pick = close.stream().filter(Product::inStock)
                        .min(Comparator.comparingLong(Product::effectivePrice)).orElse(close.get(0));
                locked.putIfAbsent(pick.getCategory(), pick);
            } else {
                ambiguous.put(phrase, close.stream().limit(4).toList());
            }
        }
        return new Resolution(locked, unresolved, ambiguous);
    }

    /** Share of the phrase's words found in the product; 0 if a model number is missing. */
    static double score(String phrase, Product p) {
        Set<String> want = tokens(phrase);
        if (want.isEmpty()) return 0;
        Set<String> have = tokens(p.getBrand() + " " + p.getName() + " " + p.getId());
        for (String w : want) {
            if (w.chars().anyMatch(Character::isDigit) && !have.contains(w)) return 0;
        }
        boolean wantsTi = want.contains("ti");
        boolean hasTi = have.contains("ti");
        if (wantsTi != hasTi && p.getCategory() == ProductCategory.GPU) return 0;
        long hit = want.stream().filter(have::contains).count();
        return (double) hit / want.size();
    }

    static Set<String> tokens(String s) {
        Set<String> out = new LinkedHashSet<>();
        String norm = s.toLowerCase(Locale.ROOT)
                .replaceAll("(?<=[a-z])-(?=\\d)", " ")   // i5-12400f -> i5 12400f
                .replaceAll("[^a-z0-9 ]", " ");
        for (String t : norm.split("\\s+")) {
            if (t.isBlank() || Set.of("the", "a", "an", "with", "and", "gb", "graphics", "card", "processor", "core",
                    "gaming", "oc", "edition").contains(t)) continue;
            out.add(t);
        }
        return out;
    }

    private record Scored(Product product, double score) { }
}
