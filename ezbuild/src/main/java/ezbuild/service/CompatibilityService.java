package ezbuild.service;

import ezbuild.dto.CompatReport;
import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compatibility engine. Pure Java, no database, easy to unit test. Errors make a build invalid; warnings are
 * shown but allowed.
 */
@Service
public class CompatibilityService {

    private static final int[] PSU_STEPS = {450, 500, 550, 600, 650, 750, 850, 1000, 1200, 1600};

    public CompatReport check(Collection<Product> products, boolean requireComplete) {
        Map<ProductCategory, Product> parts = new EnumMap<>(ProductCategory.class);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Product p : products) {
            if (p == null) continue;
            if (parts.containsKey(p.getCategory()) && p.getCategory().isBuildPart()
                    && p.getCategory() != ProductCategory.STORAGE) {
                errors.add("Only one " + p.getCategory().getLabel().toLowerCase(Locale.ROOT) + " can be used in a build.");
            }
            parts.putIfAbsent(p.getCategory(), p);
            if (!p.inStock()) warnings.add(p.getName() + " is currently out of stock.");
        }

        Product cpu = parts.get(ProductCategory.CPU);
        Product mb = parts.get(ProductCategory.MOTHERBOARD);
        Product ram = parts.get(ProductCategory.RAM);
        Product gpu = parts.get(ProductCategory.GPU);
        Product psu = parts.get(ProductCategory.PSU);
        Product cse = parts.get(ProductCategory.CASE);
        Product cooler = parts.get(ProductCategory.COOLER);

        // socket
        if (cpu != null && mb != null && !eq(cpu.spec("socket"), mb.spec("socket"))) {
            errors.add("CPU socket " + cpu.spec("socket") + " does not fit the " + mb.spec("socket") + " motherboard.");
        }
        // memory type: board vs RAM, and CPU support
        if (ram != null && mb != null && !eq(ram.spec("type"), mb.spec("ram"))) {
            errors.add(ram.spec("type") + " memory does not fit a " + mb.spec("ram") + " motherboard.");
        }
        if (cpu != null && ram != null && !cpuSupports(cpu, ram.spec("type"))) {
            errors.add(cpu.getName() + " does not support " + ram.spec("type") + " memory.");
        }
        if (cpu != null && mb != null && ram == null && mb.spec("ram") != null && !cpuSupports(cpu, mb.spec("ram"))) {
            errors.add(cpu.getName() + " does not support the board's " + mb.spec("ram") + " memory.");
        }
        // form factor
        if (mb != null && cse != null && !caseFits(cse.spec("form"), mb.spec("form"))) {
            errors.add("A " + mb.spec("form") + " motherboard does not fit the " + cse.spec("form") + " case.");
        }
        // GPU length vs case
        if (gpu != null && cse != null && gpu.specInt("len") > 0 && cse.specInt("maxgpu") > 0
                && gpu.specInt("len") > cse.specInt("maxgpu")) {
            errors.add("The graphics card (" + gpu.specInt("len") + " mm) is longer than the case allows ("
                    + cse.specInt("maxgpu") + " mm).");
        }
        // display output
        if (cpu != null && gpu == null && !cpu.specBool("igpu")) {
            errors.add(cpu.getName() + " has no integrated graphics, so the build needs a graphics card.");
        }
        // cooling
        if (cpu != null && cooler != null && cooler.specInt("tdp") > 0 && cooler.specInt("tdp") < coolerNeed(cpu)) {
            warnings.add("The cooler is rated for " + cooler.specInt("tdp") + " W but the CPU can draw up to "
                    + cpuPeak(cpu) + " W under full load; it will run hot and may throttle.");
        }
        if (cpu != null && cooler != null && cse != null && cooler.specInt("height") > 0 && cse.specInt("maxcooler") > 0
                && cooler.specInt("height") > cse.specInt("maxcooler")) {
            errors.add("The cooler is too tall for this case.");
        }
        if (cpu != null && cooler == null && (cpu.specInt("tdp") > 65 || noStockCooler(cpu))) {
            warnings.add(cpu.getName() + " has no box cooler (or needs a stronger one); add a CPU cooler.");
        }
        // power: CPU peak (turbo/PPT, not box TDP) + GPU + the rest, needs 35% headroom
        int estimate = estimatedWattage(cpu, gpu);
        if (psu != null && (cpu != null || gpu != null)) {
            int watt = psu.specInt("watt");
            int recommended = recommendedPsu(cpu, gpu);
            if (watt < recommended) {
                errors.add("The " + watt + " W power supply is too weak: this build can peak at about " + estimate
                        + " W, so it needs at least " + recommended + " W for safe headroom and power spikes.");
            } else if (highPower(cpu, gpu) && !String.valueOf(psu.spec("eff")).toUpperCase(Locale.ROOT).matches(".*(GOLD|PLATINUM|TITANIUM).*")
                    && psu.spec("eff") != null) {
                warnings.add("For a build peaking around " + estimate + " W, an 80+ Gold power supply is the safer choice.");
            }
        }
        // balance: CPU vs GPU gaming index (both roughly 0-100)
        String bottleneck = bottleneck(cpu, gpu);
        if (bottleneck != null) warnings.add(bottleneck);
        // older AM4 boards may need a BIOS update for Ryzen 5000
        if (cpu != null && mb != null && "AM4".equals(mb.spec("socket"))
                && mb.getName().matches("(?i).*\\b(A520|B450|A320)\\b.*") && cpu.getName().matches("(?i).*Ryzen \\d 5\\d{3}.*")) {
            warnings.add("This board may need a BIOS update before it runs a Ryzen 5000 CPU.");
        }

        if (requireComplete) {
            for (ProductCategory c : List.of(ProductCategory.CPU, ProductCategory.MOTHERBOARD, ProductCategory.RAM,
                    ProductCategory.STORAGE, ProductCategory.PSU, ProductCategory.CASE)) {
                if (!parts.containsKey(c)) errors.add("Missing: " + c.getLabel() + ".");
            }
        }
        return new CompatReport(errors.isEmpty(), errors, warnings, estimate);
    }

    /** Realistic peak draw: CPU turbo/PPT + GPU board power + ~75 W for board, RAM, drives and fans. */
    public int estimatedWattage(Product cpu, Product gpu) {
        int cpuW = cpu == null ? 0 : cpuPeak(cpu);
        int gpuW = gpu == null ? 0 : gpu.specInt("tdp");
        return cpuW + gpuW + 75;
    }

    /** Peak x 1.35 (spikes, ageing, efficiency sweet spot), rounded up to a standard PSU size. */
    public int recommendedPsu(Product cpu, Product gpu) {
        int needed = (int) Math.ceil(estimatedWattage(cpu, gpu) * 1.35);
        if (gpu != null && gpu.specInt("psu") > needed) needed = gpu.specInt("psu");
        for (int step : PSU_STEPS) {
            if (step >= needed) return step;
        }
        return needed;
    }

    /** Max package power: the pmax spec (Intel PL2 / AMD PPT), else TDP x 1.35. */
    public static int cpuPeak(Product cpu) {
        if (cpu == null) return 0;
        int pmax = cpu.specInt("pmax");
        return pmax > 0 ? pmax : (int) Math.round(cpu.specInt("tdp") * 1.35);
    }

    /** Cooler rating needed: about 85% of peak power. */
    public static int coolerNeed(Product cpu) {
        return (int) Math.round(cpuPeak(cpu) * 0.85);
    }

    public static boolean highPower(Product cpu, Product gpu) {
        return cpuPeak(cpu) >= 180 || (gpu != null && gpu.specInt("tdp") >= 220);
    }

    /**
     * Gaming balance check. Severe pairs are rejected by the planner; milder ones become a card warning. Null
     * when balanced or scores are missing.
     */
    public static String bottleneck(Product cpu, Product gpu) {
        double ratio = bottleneckRatio(cpu, gpu);
        if (Double.isNaN(ratio)) return null;
        if (ratio < 0.75) return "Bottleneck: the " + cpu.getName() + " will hold back the " + gpu.getName()
                + " in most games; a faster CPU would unlock noticeably more FPS.";
        if (ratio < 0.85) return "Mild CPU bottleneck: the " + gpu.getName()
                + " will be limited by the CPU in CPU-heavy games (esports titles, simulations) at 1080p.";
        if (ratio > 2.2) return "The " + gpu.getName() + " is much weaker than the " + cpu.getName()
                + "; the graphics card will limit gaming performance.";
        return null;
    }

    /** CPU gaming index / GPU gaming index; NaN when unknown. */
    public static double bottleneckRatio(Product cpu, Product gpu) {
        if (cpu == null || gpu == null) return Double.NaN;
        int c = cpu.specInt("gaming");
        int g = gpu.specInt("gaming");
        if (c == 0 || g == 0) return Double.NaN;
        return (double) c / g;
    }

    public static boolean cpuSupports(Product cpu, String memoryType) {
        if (memoryType == null) return true;
        String supported = cpu.spec("ram");
        if (supported == null) {
            String socket = cpu.spec("socket");
            if ("AM5".equals(socket)) supported = "DDR5";
            else if ("LGA1700".equals(socket)) supported = "DDR4/DDR5";
            else supported = "DDR4";
        }
        return supported.toUpperCase(Locale.ROOT).contains(memoryType.toUpperCase(Locale.ROOT));
    }

    public static boolean caseFits(String caseForm, String boardForm) {
        if (caseForm == null || boardForm == null) return true;
        int c = formRank(caseForm);
        int b = formRank(boardForm);
        return b <= c;
    }

    private static int formRank(String form) {
        String f = form.toUpperCase(Locale.ROOT);
        if (f.contains("E-ATX") || f.contains("EATX")) return 4;
        if (f.contains("MATX") || f.contains("MICRO")) return 2;
        if (f.contains("ITX")) return 1;
        return 3; // ATX
    }

    private static boolean noStockCooler(Product cpu) {
        String n = cpu.getName().toUpperCase(Locale.ROOT);
        return n.matches(".*\\d{4,5}KF?\\b.*") || n.contains("X3D") || n.matches(".*RYZEN \\d 9\\d{3}X.*");
    }

    private static boolean eq(String a, String b) {
        return a == null || b == null || a.equalsIgnoreCase(b);
    }
}
