package ezbuild;

import ezbuild.model.Product;
import ezbuild.model.ProductCategory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small fixture catalogue shared by the planner and compatibility tests. */
public final class Fixtures {

    private Fixtures() { }

    public static Product p(String id, ProductCategory c, String brand, String name, long price, int perf, Object... kv) {
        Product p = new Product();
        p.setId(id);
        p.setCategory(c);
        p.setBrand(brand);
        p.setName(name);
        p.setPrice(price);
        p.setStock(10);
        p.setActive(true);
        p.setPerfScore(perf);
        Map<String, Object> specs = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) specs.put((String) kv[i], kv[i + 1]);
        p.setSpecs(specs);
        p.refreshSalePrice();
        return p;
    }

    public static List<Product> catalogue() {
        List<Product> list = new ArrayList<>();
        list.add(p("cpu-02", ProductCategory.CPU, "AMD", "AMD Ryzen 5 5600", 12200, 55, "socket", "AM4", "tdp", 65, "igpu", false, "ram", "DDR4"));
        list.add(p("cpu-06", ProductCategory.CPU, "AMD", "AMD Ryzen 5 7600", 21500, 70, "socket", "AM5", "tdp", 65, "igpu", true, "ram", "DDR5"));
        list.add(p("cpu-12", ProductCategory.CPU, "Intel", "Intel Core i5-12400F", 14500, 58, "socket", "LGA1700", "tdp", 65, "igpu", false, "ram", "DDR4/DDR5"));
        list.add(p("cpu-14", ProductCategory.CPU, "Intel", "Intel Core i5-14600K", 35500, 85, "socket", "LGA1700", "tdp", 125, "igpu", true, "ram", "DDR4/DDR5"));
        list.add(p("cpu-10", ProductCategory.CPU, "Intel", "Intel Core i5-10400", 14000, 42, "socket", "LGA1200", "tdp", 65, "igpu", true, "ram", "DDR4"));
        list.add(p("mb-03", ProductCategory.MOTHERBOARD, "ASRock", "ASRock B550M-HDV", 9800, 0, "socket", "AM4", "ram", "DDR4", "form", "mATX"));
        list.add(p("mb-05", ProductCategory.MOTHERBOARD, "Gigabyte", "Gigabyte B650M K", 14500, 0, "socket", "AM5", "ram", "DDR5", "form", "mATX"));
        list.add(p("mb-10", ProductCategory.MOTHERBOARD, "Gigabyte", "Gigabyte B760M DS3H DDR4", 13500, 0, "socket", "LGA1700", "ram", "DDR4", "form", "mATX"));
        list.add(p("mb-11", ProductCategory.MOTHERBOARD, "MSI", "MSI PRO B760M-P DDR5", 15800, 0, "socket", "LGA1700", "ram", "DDR5", "form", "mATX"));
        list.add(p("mb-08", ProductCategory.MOTHERBOARD, "Gigabyte", "Gigabyte H510M K V2", 7800, 0, "socket", "LGA1200", "ram", "DDR4", "form", "mATX"));
        list.add(p("ram-02", ProductCategory.RAM, "Corsair", "Corsair Vengeance LPX 16GB DDR4 3200", 15000, 0, "type", "DDR4", "gb", 16, "mhz", 3200));
        list.add(p("ram-05", ProductCategory.RAM, "AITC", "AITC Master 16GB DDR5 6000", 27000, 0, "type", "DDR5", "gb", 16, "mhz", 6000));
        list.add(p("ram-06", ProductCategory.RAM, "Corsair", "Corsair Vengeance 32GB DDR5 6000", 55000, 0, "type", "DDR5", "gb", 32, "mhz", 6000));
        list.add(p("gpu-01", ProductCategory.GPU, "AFOX", "AFOX GT1030 2GB", 11500, 8, "vram", 2, "tdp", 30, "len", 170, "psu", 300));
        list.add(p("gpu-06", ProductCategory.GPU, "ZOTAC", "ZOTAC GAMING GeForce RTX 5060 Twin Edge 8GB", 55500, 55, "vram", 8, "tdp", 145, "len", 230, "psu", 550));
        list.add(p("gpu-05", ProductCategory.GPU, "PowerColor", "PowerColor Fighter Radeon RX 7600 8GB", 46499, 45, "vram", 8, "tdp", 165, "len", 240, "psu", 550));
        list.add(p("gpu-12", ProductCategory.GPU, "Sapphire", "Sapphire PULSE Radeon RX 9070 16GB", 110900, 88, "vram", 16, "tdp", 220, "len", 330, "psu", 700));
        list.add(p("ssd-02", ProductCategory.STORAGE, "Kingston", "Kingston NV2 1TB NVMe", 7500, 0, "iface", "NVMe", "gb", 1000));
        list.add(p("ssd-01", ProductCategory.STORAGE, "Kingston", "Kingston NV2 500GB NVMe", 4300, 0, "iface", "NVMe", "gb", 500));
        list.add(p("psu-01", ProductCategory.PSU, "Corsair", "Corsair CV450", 4900, 0, "watt", 450));
        list.add(p("psu-03", ProductCategory.PSU, "Corsair", "Corsair CX650", 8500, 0, "watt", 650));
        list.add(p("psu-04", ProductCategory.PSU, "Gigabyte", "Gigabyte P750GM", 12500, 0, "watt", 750));
        list.add(p("case-01", ProductCategory.CASE, "Antec", "Antec NX200M", 3800, 0, "form", "mATX", "maxgpu", 300));
        list.add(p("case-05", ProductCategory.CASE, "Corsair", "Corsair 4000D Airflow", 13500, 0, "form", "ATX", "maxgpu", 360));
        list.add(p("cool-01", ProductCategory.COOLER, "Deepcool", "Deepcool AG400", 2300, 0, "type", "air", "tdp", 150, "height", 150));
        return list;
    }

    public static Product find(String id) {
        return catalogue().stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
    }
}
