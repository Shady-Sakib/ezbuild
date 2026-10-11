package ezbuild.dto;

import ezbuild.model.Product;

public record CartLine(String productId, String name, String brand, String category, long unitPrice,
                       long listPrice, int discountPercent, int qty, int stock) {

    public static CartLine of(Product p, int qty) {
        return new CartLine(p.getId(), p.getName(), p.getBrand(), p.getCategory().getLabel(), p.effectivePrice(),
                p.getPrice(), p.activeDiscount(), qty, p.getStock());
    }

    public long lineTotal() {
        return unitPrice * qty;
    }

    public long lineSaving() {
        return (listPrice - unitPrice) * qty;
    }

    public boolean overStock() {
        return qty > stock;
    }

    public String image() {
        return "/media/p/" + productId + ".webp";
    }
}
