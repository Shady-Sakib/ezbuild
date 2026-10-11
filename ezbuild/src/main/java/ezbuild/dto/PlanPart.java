package ezbuild.dto;

import ezbuild.model.Product;
import ezbuild.model.ProductCategory;

public record PlanPart(ProductCategory category, String productId, String name, String brand,
                       long price, long listPrice, int qty) {

    public static PlanPart of(Product p) {
        return new PlanPart(p.getCategory(), p.getId(), p.getName(), p.getBrand(),
                p.effectivePrice(), p.getPrice(), 1);
    }

    public long lineTotal() {
        return price * qty;
    }

    public String image() {
        return "/media/p/" + productId + ".webp";
    }
}
