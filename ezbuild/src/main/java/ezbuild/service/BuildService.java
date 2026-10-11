package ezbuild.service;

import ezbuild.build.BuildPlanner;
import ezbuild.dto.BuildPlan;
import ezbuild.dto.CompatReport;
import ezbuild.model.Build;
import ezbuild.model.BuildSource;
import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import ezbuild.repository.BuildRepository;
import ezbuild.util.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Manual builder checks, saved builds and share links. */
@Service
@RequiredArgsConstructor
public class BuildService {

    public static final int MAX_SAVED = 30;

    private final BuildRepository buildRepository;
    private final ProductService productService;
    private final CompatibilityService compatibilityService;
    private final BuildPlanner planner;

    /** Live check for the builder page and POST /api/builds/validate. */
    public BuildPlan validate(Collection<String> productIds) {
        List<Product> products = new ArrayList<>(productService.byIds(productIds).values());
        CompatReport report = compatibilityService.check(products, false);
        Map<ProductCategory, Product> parts = new EnumMap<>(ProductCategory.class);
        products.forEach(p -> parts.putIfAbsent(p.getCategory(), p));
        return planner.toPlan("Custom", parts, 0, report);
    }

    public Build save(String userId, String name, BuildSource source, Collection<String> productIds, String note) {
        if (buildRepository.findByUserIdOrderByCreatedAtDesc(userId).size() >= MAX_SAVED) {
            throw new BusinessException("You can keep up to " + MAX_SAVED + " saved builds. Delete one first.");
        }
        Map<String, Product> products = productService.byIds(productIds);
        if (products.isEmpty()) throw new BusinessException("Add at least one part before saving.");
        Build build = new Build();
        build.setUserId(userId);
        build.setName(name == null || name.isBlank() ? "My build" : name.trim());
        build.setSource(source);
        build.setNote(note);
        build.setShareCode(Hashing.code(6).toLowerCase());
        for (Product p : products.values()) {
            Build.BuildPart part = new Build.BuildPart();
            part.setCategory(p.getCategory());
            part.setProductId(p.getId());
            part.setNameSnap(p.getName());
            part.setPriceSnap(p.effectivePrice());
            build.getParts().add(part);
        }
        build.setTotal(build.getParts().stream().mapToLong(Build.BuildPart::getPriceSnap).sum());
        build.setCreatedAt(Instant.now());
        return buildRepository.save(build);
    }

    public List<Build> list(String userId) {
        return buildRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Someone else's build id is a 404. */
    public Build getOwned(String id, String userId) {
        return buildRepository.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Build not found."));
    }

    public Build getShared(String code) {
        return buildRepository.findByShareCode(code).orElseThrow(() -> new NotFoundException("Build not found."));
    }

    public void delete(String id, String userId) {
        buildRepository.delete(getOwned(id, userId));
    }

    public List<String> productIds(Build build) {
        return build.getParts().stream().map(Build.BuildPart::getProductId).toList();
    }
}
