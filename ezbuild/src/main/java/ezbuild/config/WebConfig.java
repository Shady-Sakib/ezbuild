package ezbuild.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * /media/p/{sku}.webp is served from the upload folder first (admin uploads, outside the jar), then from the
 * bundled static/media images.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploads = Path.of(props.uploadDir()).toAbsolutePath().toUri().toString();
        if (!uploads.endsWith("/")) uploads += "/";
        registry.addResourceHandler("/media/**")
                .addResourceLocations(uploads, "classpath:/static/media/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
    }
}
