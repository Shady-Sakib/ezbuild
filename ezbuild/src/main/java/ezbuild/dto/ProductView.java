package ezbuild.dto;

import ezbuild.model.Product;

import java.util.Map;

/** Product as returned by the REST API (never the raw document). */
public record ProductView(String id, String name, String brand, String category, String categoryLabel,
                          long price, long listPrice, int discountPercent, boolean inStock, boolean lowStock,
                          String image, String summary, Map<String, Object> specs, double rating, int ratingCount) {

    public static ProductView of(Product p) {
        return new ProductView(p.getId(), p.getName(), p.getBrand(), p.getCategory().name(),
                p.getCategory().getLabel(), p.effectivePrice(), p.getPrice(), p.activeDiscount(), p.inStock(),
                p.lowStock(), "/media/p/" + p.getId() + ".webp", p.getSummary(), p.getSpecs(), p.getRatingAvg(),
                p.getRatingCount());
    }
}
