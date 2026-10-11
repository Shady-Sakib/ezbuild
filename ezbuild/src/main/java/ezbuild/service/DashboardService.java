package ezbuild.service;

import ezbuild.dto.DashboardStats;
import ezbuild.dto.FacetCount;
import ezbuild.model.Order;
import ezbuild.model.OrderStatus;
import ezbuild.model.Role;
import ezbuild.repository.OrderRepository;
import ezbuild.repository.ProductRepository;
import ezbuild.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Admin dashboard figures via MongoTemplate aggregations. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    public static final List<OrderStatus> REVENUE = List.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED,
            OrderStatus.DELIVERED);

    private final MongoTemplate mongoTemplate;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SupportService supportService;

    public DashboardStats stats() {
        Criteria counted = Criteria.where("status").in(REVENUE);

        Document rev = mongoTemplate.aggregate(Aggregation.newAggregation(Aggregation.match(counted),
                        Aggregation.group().sum("total").as("revenue").count().as("orders")), Order.class, Document.class)
                .getUniqueMappedResult();
        long revenue = rev == null ? 0 : num(rev.get("revenue"));

        // last 14 days, one point per day (Dhaka time)
        Instant since = Instant.now().minus(Duration.ofDays(13)).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        List<Document> days = mongoTemplate.aggregate(Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").in(REVENUE).and("createdAt").gte(since)),
                Aggregation.project("total").and(DateOperators.dateOf("createdAt").withTimezone(
                        DateOperators.Timezone.valueOf("Asia/Dhaka")).toString("%Y-%m-%d")).as("day"),
                Aggregation.group("day").sum("total").as("revenue").count().as("orders"),
                Aggregation.sort(Sort.Direction.ASC, "_id")), Order.class, Document.class).getMappedResults();
        Map<String, DashboardStats.SalesPoint> byDay = new LinkedHashMap<>();
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Dhaka")).minusDays(13);
        for (int i = 0; i < 14; i++) {
            String d = start.plusDays(i).toString();
            byDay.put(d, new DashboardStats.SalesPoint(d.substring(5), 0, 0));
        }
        for (Document d : days) {
            String key = String.valueOf(d.get("_id"));
            if (byDay.containsKey(key)) {
                byDay.put(key, new DashboardStats.SalesPoint(key.substring(5), num(d.get("revenue")), num(d.get("orders"))));
            }
        }

        List<Document> top = mongoTemplate.aggregate(Aggregation.newAggregation(
                Aggregation.match(counted),
                Aggregation.unwind("items"),
                Aggregation.project("items").and(ArithmeticOperators.Multiply.valueOf("items.unitPrice")
                        .multiplyBy("items.qty")).as("line"),
                Aggregation.group("items.productId").first("items.name").as("name").sum("items.qty").as("qty")
                        .sum("line").as("revenue"),
                Aggregation.sort(Sort.Direction.DESC, "qty"),
                Aggregation.limit(5)), Order.class, Document.class).getMappedResults();
        List<DashboardStats.TopSeller> sellers = new ArrayList<>();
        for (Document d : top) {
            sellers.add(new DashboardStats.TopSeller(String.valueOf(d.get("_id")), String.valueOf(d.get("name")),
                    num(d.get("qty")), num(d.get("revenue"))));
        }

        List<FacetCount> statusCounts = count("status");
        List<FacetCount> paymentMix = count("paymentMethod");

        return new DashboardStats(revenue, orderRepository.count(),
                orderRepository.countByStatusIn(List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PLACED, OrderStatus.PAID,
                        OrderStatus.PROCESSING)),
                userRepository.countByRolesContaining(Role.ROLE_USER), productRepository.countByActiveTrue(),
                productRepository.countLowStock(3), supportService.openCount(),
                new ArrayList<>(byDay.values()), sellers, statusCounts, paymentMix);
    }

    private List<FacetCount> count(String field) {
        List<Document> docs = mongoTemplate.aggregate(Aggregation.newAggregation(
                Aggregation.group(field).count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count")), Order.class, Document.class).getMappedResults();
        List<FacetCount> out = new ArrayList<>();
        for (Document d : docs) {
            if (d.get("_id") != null) out.add(new FacetCount(String.valueOf(d.get("_id")), num(d.get("count"))));
        }
        return out;
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }
}
