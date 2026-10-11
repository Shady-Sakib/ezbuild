package ezbuild.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Catalogue item. The id is the SKU (cpu-01, gpu-07...) and also the image name: /media/p/{id}.webp */
@Document("products")
@CompoundIndexes({
        @CompoundIndex(name = "cat_brand_price", def = "{'category': 1, 'brand': 1, 'price': 1}"),
        @CompoundIndex(name = "active_cat", def = "{'active': 1, 'category': 1}")
})
@Getter
@Setter
public class Product {

    @Id
    @NotBlank(message = "SKU is required.")
    @Pattern(regexp = "^[a-z]{2,4}-\\d{2,3}$", message = "SKU looks like cpu-01 or gpu-12.")
    private String id;

    @NotBlank(message = "Name is required.")
    @Size(max = 140)
    @TextIndexed(weight = 3)
    private String name;

    @NotBlank(message = "Brand is required.")
    @TextIndexed(weight = 2)
    @Indexed
    private String brand;

    @NotNull(message = "Category is required.")
    private ProductCategory category;

    /** List price in whole BDT. */
    @Min(value = 1, message = "Price must be at least 1 BDT.")
    @Indexed
    private long price;

    /** Copy of effectivePrice() so the shop can filter/sort by what the customer pays. */
    @Indexed
    private long salePrice;

    @Min(value = 0, message = "Discount cannot be negative.")
    @Max(value = 90, message = "Discount can be at most 90%.")
    private int discountPercent;

    private LocalDate discountStart;

    private LocalDate discountEnd;

    @Min(value = 0, message = "Stock cannot be negative.")
    private int stock;

    private boolean active = true;

    @Size(max = 300)
    @TextIndexed
    private String summary;

    @Size(max = 4000)
    private String description;

    /** Free-form specs; used by filters, the compatibility engine, the planner and the AI. */
    private Map<String, Object> specs = new LinkedHashMap<>();

    /** Overall CPU/GPU performance score used by the planner when detailed scores are missing. */
    private int perfScore;

    private List<String> tags = new ArrayList<>();

    /** verified-2026 or approx */
    private String priceStatus = "approx";

    private double ratingAvg;

    private int ratingCount;

    private long soldCount;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /** True when a discount is set and today is inside its window. */
    public boolean onSale() {
        if (discountPercent <= 0) return false;
        LocalDate today = LocalDate.now();
        if (discountStart != null && today.isBefore(discountStart)) return false;
        return discountEnd == null || !today.isAfter(discountEnd);
    }

    /** The one place the selling price is computed (cards, cart, checkout, planner). */
    public long effectivePrice() {
        if (!onSale()) return price;
        return Math.round(price * (100 - discountPercent) / 100.0);
    }

    /** Call before every save (ProductService does) and nightly for discount windows. */
    public void refreshSalePrice() {
        this.salePrice = effectivePrice();
    }

    public int activeDiscount() {
        return onSale() ? discountPercent : 0;
    }

    public boolean inStock() {
        return stock > 0;
    }

    public boolean lowStock() {
        return stock > 0 && stock <= 3;
    }

    public String spec(String key) {
        Object v = specs == null ? null : specs.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public int effectivePriceInt() {
        return (int) Math.min(Integer.MAX_VALUE, effectivePrice());
    }

    public int specInt(String key) {
        Object v = specs == null ? null : specs.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return 0;
        try {
            return (int) Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean specBool(String key) {
        Object v = specs == null ? null : specs.get(key);
        return v instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(v));
    }
}
