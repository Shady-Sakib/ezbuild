package ezbuild.model;

import java.util.Arrays;
import java.util.List;

/** The first eight are build parts; the rest are add-ons that never block a build. */
public enum ProductCategory {
    CPU("Processor", "cpu", true),
    MOTHERBOARD("Motherboard", "mb", true),
    RAM("Memory (RAM)", "ram", true),
    GPU("Graphics Card", "gpu", true),
    STORAGE("Storage", "ssd", true),
    PSU("Power Supply", "psu", true),
    CASE("Casing", "case", true),
    COOLER("CPU Cooler", "cool", true),
    MONITOR("Monitor", "mon", false),
    KEYBOARD("Keyboard", "kb", false),
    MOUSE("Mouse", "ms", false),
    HEADSET("Headset", "hs", false);

    private final String label;
    private final String code;
    private final boolean buildPart;

    ProductCategory(String label, String code, boolean buildPart) {
        this.label = label;
        this.code = code;
        this.buildPart = buildPart;
    }

    public String getLabel() {
        return label;
    }

    public String getCode() {
        return code;
    }

    public boolean isBuildPart() {
        return buildPart;
    }

    public String slug() {
        return name().toLowerCase();
    }

    public static List<ProductCategory> buildParts() {
        return Arrays.stream(values()).filter(ProductCategory::isBuildPart).toList();
    }

    public static ProductCategory fromSlug(String value) {
        if (value == null || value.isBlank()) return null;
        for (ProductCategory c : values()) {
            if (c.name().equalsIgnoreCase(value) || c.code.equalsIgnoreCase(value)) return c;
        }
        return null;
    }
}
