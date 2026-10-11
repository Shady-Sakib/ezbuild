package ezbuild.controller;

import ezbuild.dto.ProductFilter;
import ezbuild.model.ProductCategory;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the shop query string into a ProductFilter (used by /products and GET /api/products). e.g.
 * ?q=&category=gpu&brand=MSI&brand=ASUS&min=&max=&inStock=1&sale=1&sort=price_asc&page=0&f.vram=8
 */
public final class FilterParams {

    private FilterParams() { }

    public static ProductFilter from(HttpServletRequest req) {
        Map<String, List<String>> specs = new LinkedHashMap<>();
        req.getParameterMap().forEach((k, v) -> {
            if (k.startsWith("f.") && k.length() > 2) {
                specs.put(k.substring(2), Arrays.stream(v).filter(s -> !s.isBlank()).toList());
            }
        });
        String[] brands = req.getParameterValues("brand");
        return new ProductFilter(
                trim(req.getParameter("q")),
                ProductCategory.fromSlug(req.getParameter("category")),
                brands == null ? List.of() : Arrays.asList(brands),
                parseLong(req.getParameter("min")),
                parseLong(req.getParameter("max")),
                flag(req.getParameter("inStock")),
                flag(req.getParameter("sale")),
                specs,
                req.getParameter("sort"),
                parseInt(req.getParameter("page"), 0),
                parseInt(req.getParameter("size"), 24));
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > 80 ? t.substring(0, 80) : t;
    }

    private static boolean flag(String v) {
        return v != null && (v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on"));
    }

    private static Long parseLong(String v) {
        try {
            return v == null || v.isBlank() ? null : Long.parseLong(v.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseInt(String v, int fallback) {
        try {
            return v == null || v.isBlank() ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
