package ezbuild.config;

import ezbuild.model.Address;
import ezbuild.model.Order;
import ezbuild.model.OrderStatus;
import ezbuild.model.PaymentMethod;
import ezbuild.model.PaymentStatus;
import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import ezbuild.model.Role;
import ezbuild.model.ShippingZone;
import ezbuild.model.User;
import ezbuild.repository.OrderRepository;
import ezbuild.repository.ProductRepository;
import ezbuild.repository.UserRepository;
import ezbuild.util.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs when ezbuild.seed=true. Safe to re-run: inserts missing products, users and demo orders only, and
 * refreshes specs/summaries of existing products without touching price, stock or discounts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ezbuild.seed", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    @Override
    public void run(String... args) throws Exception {
        int products = seedProducts();
        seedUsers();
        seedDemoOrders();
        log.info("Seed complete: {} new products. Sign in as admin / {} (local only).", products,
                props.seedAdminPassword().equals("Admin@123") ? "Admin@123" : "SEED_ADMIN_PASSWORD");
    }

    private int seedProducts() throws Exception {
        List<Product> toSave = new ArrayList<>();
        List<Product> refreshed = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("seed/products.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) return 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> c = parseCsvLine(line);
                String id = c.get(0);
                Product existing = productRepository.findById(id).orElse(null);
                if (existing != null) {
                    // existing product: specs, scores and texts follow the CSV;
                    // price, stock and discounts stay as the admin set them
                    Map<String, Object> specs = parseSpecs(c.get(10));
                    if (!specs.equals(existing.getSpecs()) || existing.getPerfScore() != Integer.parseInt(c.get(8))
                            || !c.get(9).equals(existing.getSummary())) {
                        existing.setPerfScore(Integer.parseInt(c.get(8)));
                        existing.setSummary(c.get(9));
                        existing.setSpecs(specs);
                        existing.setDescription(describe(existing));
                        refreshed.add(existing);
                    }
                    continue;
                }
                Product p = new Product();
                p.setId(id);
                p.setCategory(ProductCategory.valueOf(c.get(1)));
                p.setBrand(c.get(2));
                p.setName(c.get(3));
                p.setPrice(Long.parseLong(c.get(4)));
                p.setDiscountPercent(Integer.parseInt(c.get(5)));
                p.setStock(Integer.parseInt(c.get(6)));
                p.setPriceStatus(c.get(7));
                p.setPerfScore(Integer.parseInt(c.get(8)));
                p.setSummary(c.get(9));
                p.setSpecs(parseSpecs(c.get(10)));
                p.setDescription(describe(p));
                p.setTags(List.of(p.getCategory().getCode(), p.getBrand().toLowerCase()));
                p.setActive(true);
                p.setCreatedAt(Instant.now());
                p.setUpdatedAt(Instant.now());
                p.refreshSalePrice();
                toSave.add(p);
            }
        }
        productRepository.saveAll(toSave);
        if (!refreshed.isEmpty()) {
            productRepository.saveAll(refreshed);
            log.info("Catalogue refresh: updated specs/summary on {} existing products.", refreshed.size());
        }
        return toSave.size();
    }

    private void seedUsers() {
        createUser("admin", "admin@ezbuild.app", props.seedAdminPassword(), EnumSet.of(Role.ROLE_ADMIN, Role.ROLE_USER),
                "EZBuild Admin");
        createUser("staff", "staff@ezbuild.app", "Staff@123", EnumSet.of(Role.ROLE_STAFF, Role.ROLE_USER), "Store Staff");
        createUser("rahim", "rahim@example.com", "User@1234", EnumSet.of(Role.ROLE_USER), "Rahim Ahmed");
        createUser("nusrat", "nusrat@example.com", "User@1234", EnumSet.of(Role.ROLE_USER), "Nusrat Jahan");
        createUser("tanvir", "tanvir@example.com", "User@1234", EnumSet.of(Role.ROLE_USER), "Tanvir Hasan");
    }

    private void createUser(String username, String email, String password, Set<Role> roles, String displayName) {
        if (userRepository.existsByUsernameIgnoreCase(username)) return;
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(password));
        u.setRoles(roles);
        u.getProfile().setDisplayName(displayName);
        u.setJoinedAt(Instant.now());
        userRepository.save(u);
    }

    /** A few demo orders over the last two weeks so the dashboard isn't empty on first run. */
    private void seedDemoOrders() {
        if (orderRepository.count() > 0) return;
        User rahim = userRepository.findByUsernameIgnoreCase("rahim").orElse(null);
        if (rahim == null) return;
        String[][] baskets = {
                {"cpu-02", "mb-03", "ram-02", "ssd-02"}, {"gpu-06", "psu-03"}, {"cpu-06", "mb-05", "ram-05"},
                {"mon-02", "kb-02", "ms-01"}, {"gpu-03", "case-05"}, {"ssd-04", "hs-01"}, {"cpu-12", "mb-10", "ram-04"},
                {"gpu-09", "psu-04", "cool-02"}};
        OrderStatus[] statuses = {OrderStatus.DELIVERED, OrderStatus.DELIVERED, OrderStatus.SHIPPED, OrderStatus.PAID,
                OrderStatus.PROCESSING, OrderStatus.DELIVERED, OrderStatus.PAID, OrderStatus.DELIVERED};
        for (int i = 0; i < baskets.length; i++) {
            Order o = new Order();
            o.setCode("EZDEMO" + (i + 1) + Hashing.code(1));
            o.setUserId(rahim.getId());
            o.setUsername(rahim.getUsername());
            o.setEmail(rahim.getEmail());
            Address a = new Address();
            a.setFullName("Rahim Ahmed");
            a.setPhone("01712345678");
            a.setLine1("House 12, Road 5");
            a.setArea("Dhanmondi");
            a.setCity("Dhaka");
            a.setZone(i % 3 == 0 ? ShippingZone.OUTSIDE_DHAKA : ShippingZone.INSIDE_DHAKA);
            o.setShipping(a);
            o.setShippingZone(a.getZone());
            long subtotal = 0;
            for (String sku : baskets[i]) {
                Product p = productRepository.findById(sku).orElse(null);
                if (p == null) continue;
                Order.OrderItem it = new Order.OrderItem();
                it.setProductId(p.getId());
                it.setName(p.getName());
                it.setBrand(p.getBrand());
                it.setCategory(p.getCategory());
                it.setUnitListPrice(p.getPrice());
                it.setDiscountPercent(p.activeDiscount());
                it.setUnitPrice(p.effectivePrice());
                it.setQty(1);
                o.getItems().add(it);
                subtotal += it.getUnitPrice();
            }
            o.setSubtotal(subtotal);
            o.setShippingFee(a.getZone() == ShippingZone.OUTSIDE_DHAKA ? props.shipping().outside() : props.shipping().inside());
            boolean cod = i % 2 == 0;
            o.setPaymentMethod(cod ? PaymentMethod.COD : PaymentMethod.SSLCOMMERZ);
            o.setCodFee(cod ? props.payment().codFee() : 0);
            o.setTotal(subtotal + o.getShippingFee() + o.getCodFee());
            o.setStatus(statuses[i]);
            o.setPaymentStatus(cod && statuses[i] != OrderStatus.DELIVERED ? PaymentStatus.COD_PENDING : PaymentStatus.SUCCEEDED);
            o.getHistory().add(new Order.StatusChange(statuses[i], "seed", "Demo order"));
            Instant when = Instant.now().minus(Duration.ofDays(13 - i * 1L - (i > 4 ? 3 : 0)));
            Order saved = orderRepository.save(o);
            saved.setCreatedAt(when); // auditing stamps "now" on insert, so back-date afterwards
            orderRepository.save(saved);
        }
    }

    /** Long description for the product page and the AI: summary plus every spec as "Label: value". */
    static String describe(Product p) {
        StringBuilder sb = new StringBuilder(p.getName()).append(". ").append(p.getSummary());
        if (p.getSpecs() != null && !p.getSpecs().isEmpty()) {
            sb.append(" Specifications: ");
            List<String> parts = new ArrayList<>();
            p.getSpecs().forEach((k, v) -> parts.add(ezbuild.util.Fmt.label(k) + ": " + ezbuild.util.Fmt.specValue(v)));
            sb.append(String.join("; ", parts)).append('.');
        }
        sb.append(" Genuine product with official brand warranty in Bangladesh.");
        String text = sb.toString();
        return text.length() > 4000 ? text.substring(0, 3997) + "..." : text;
    }

    static Map<String, Object> parseSpecs(String raw) {
        Map<String, Object> specs = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return specs;
        for (String pair : raw.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if ("true".equals(value) || "false".equals(value)) {
                specs.put(key, Boolean.parseBoolean(value));
            } else if (value.matches("-?\\d+")) {
                specs.put(key, Integer.parseInt(value));
            } else if (value.matches("-?\\d+\\.\\d+")) {
                specs.put(key, Double.parseDouble(value));
            } else {
                specs.put(key, value);
            }
        }
        return specs;
    }

    /** Minimal CSV line parser: commas, quoted fields, "" escapes. */
    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
