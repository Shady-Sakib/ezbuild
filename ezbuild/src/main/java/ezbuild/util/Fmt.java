package ezbuild.util;

import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Template helper, e.g. ${@fmt.tk(12200)} -> "৳12,200". */
@Component("fmt")
public class Fmt {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public static String taka(long amount) {
        return "৳" + NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }

    public String tk(long amount) {
        return taka(amount);
    }

    public String num(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    public String dateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(DHAKA));
    }

    public String date(Instant instant) {
        return instant == null ? "" : DATE.format(instant.atZone(DHAKA));
    }

    public String pct(long part, long whole) {
        if (whole <= 0) return "0%";
        return Math.round(part * 100.0 / whole) + "%";
    }

    public String stars(double rating) {
        int n = (int) Math.max(0, Math.min(5, Math.round(rating)));
        return "★".repeat(n) + "☆".repeat(5 - n);
    }

    public String specLabel(String key) {
        return label(key);
    }

    /** Readable label for a spec key (product page, AI facts, descriptions). */
    public static String label(String key) {
        if (key == null || key.isEmpty()) return "";
        return switch (key) {
            case "socket" -> "Socket";
            case "cores" -> "Cores";
            case "threads" -> "Threads";
            case "base" -> "Base clock (GHz)";
            case "boost" -> "Boost clock";
            case "l3" -> "L3 cache (MB)";
            case "tdp" -> "TDP (W)";
            case "pmax" -> "Max turbo power (W)";
            case "igpu" -> "Integrated graphics";
            case "igpuName" -> "iGPU model";
            case "ram" -> "Memory type";
            case "arch" -> "Architecture";
            case "year" -> "Launched";
            case "pcie" -> "PCIe version";
            case "boxcooler" -> "Cooler in box";
            case "st" -> "Single-core score (CB R23, approx.)";
            case "mt" -> "Multi-core score (CB R23, approx.)";
            case "gaming" -> "Gaming index (relative, higher is faster)";
            case "chip" -> "GPU";
            case "vramType" -> "Memory";
            case "busBits" -> "Memory bus (bit)";
            case "rt" -> "Ray tracing";
            case "upscaler" -> "Upscaling";
            case "psu" -> "Recommended PSU (W)";
            case "chipset" -> "Chipset";
            case "ramSlots" -> "RAM slots";
            case "wifi" -> "Wi-Fi";
            case "form" -> "Form factor";
            case "type" -> "Type";
            case "gb" -> "Capacity (GB)";
            case "mhz" -> "Speed (MHz)";
            case "cl" -> "CAS latency (CL)";
            case "kit" -> "Kit";
            case "rgb" -> "RGB";
            case "iface" -> "Interface";
            case "gen" -> "Bus";
            case "read" -> "Read (MB/s)";
            case "write" -> "Write (MB/s)";
            case "tbw" -> "Endurance (TBW)";
            case "vram" -> "VRAM (GB)";
            case "len" -> "Length (mm)";
            case "watt" -> "Wattage";
            case "eff" -> "Efficiency";
            case "modular" -> "Modular";
            case "atx3" -> "ATX 3.x";
            case "maxgpu" -> "Max GPU length (mm)";
            case "maxcooler" -> "Max cooler height (mm)";
            case "fans" -> "Fans included";
            case "height" -> "Height (mm)";
            case "inch" -> "Size (inch)";
            case "res" -> "Resolution";
            case "hz" -> "Refresh rate (Hz)";
            case "panel" -> "Panel";
            case "ms" -> "Response time (ms)";
            case "sync" -> "Adaptive sync";
            case "m2" -> "M.2 slots";
            case "dpi" -> "Max DPI";
            case "conn" -> "Connection";
            default -> Character.toUpperCase(key.charAt(0)) + key.substring(1);
        };
    }

    public String specText(Object v) {
        return specValue(v);
    }

    public static String specValue(Object v) {
        if (v instanceof Boolean b) return b ? "Yes" : "No";
        return String.valueOf(v);
    }
}
