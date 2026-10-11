package ezbuild.service;

import ezbuild.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

/**
 * Admin image uploads, saved outside the jar as {upload-dir}/p/{sku}.webp (named by SKU). Allow-list on type
 * and size.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of("image/webp", "image/jpeg", "image/png");
    private static final long MAX_BYTES = 2 * 1024 * 1024;

    private final AppProperties props;
    private final MediaResolver mediaResolver;

    public void saveProductImage(String sku, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return;
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(type)) {
            throw new IllegalArgumentException("Only WebP, JPG or PNG images are allowed.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Images must be under 2 MB.");
        }
        if (!sku.matches("^[a-z]{2,4}-\\d{2,3}$")) {
            throw new IllegalArgumentException("Invalid SKU.");
        }
        if (!"image/webp".equals(type)) {
            try (InputStream in = file.getInputStream()) {
                if (ImageIO.read(in) == null) throw new IllegalArgumentException("That file is not a valid image.");
            }
        }
        Path dir = Path.of(props.uploadDir(), "p");
        Files.createDirectories(dir);
        // browsers render JPG/PNG fine under a .webp name; one name keeps URLs stable
        Path target = dir.resolve(sku + ".webp");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        mediaResolver.forget(sku);
        log.info("Saved product image for sku={}", sku);
    }
}
