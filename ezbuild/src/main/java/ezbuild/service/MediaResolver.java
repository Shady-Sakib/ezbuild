package ezbuild.service;

import ezbuild.config.AppProperties;
import ezbuild.model.ProductCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /media/p/{sku}.webp if the image exists (uploads or bundled), else the category placeholder, so the UI
 * never shows a broken image.
 */
@Component("media")
@RequiredArgsConstructor
public class MediaResolver {

    private final AppProperties props;
    private final Map<String, Boolean> exists = new ConcurrentHashMap<>();

    public String product(String sku, ProductCategory category) {
        String file = "p/" + sku + ".webp";
        boolean found = exists.computeIfAbsent(file, this::lookup);
        if (found) return "/media/" + file;
        return placeholder(category);
    }

    public String placeholder(ProductCategory category) {
        return "/media/c/" + (category == null ? "part" : category.slug()) + ".svg";
    }

    /** Call after an admin upload so the new file is picked up. */
    public void forget(String sku) {
        exists.remove("p/" + sku + ".webp");
    }

    private boolean lookup(String file) {
        if (Files.exists(Path.of(props.uploadDir(), file))) return true;
        return new ClassPathResource("static/media/" + file).exists();
    }
}
