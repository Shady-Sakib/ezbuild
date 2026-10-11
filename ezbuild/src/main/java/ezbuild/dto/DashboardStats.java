package ezbuild.dto;

import java.util.List;

public record DashboardStats(long revenue, long orders, long pendingOrders, long customers, long products,
                             long lowStock, long openTickets, List<SalesPoint> sales, List<TopSeller> topSellers,
                             List<FacetCount> statusCounts, List<FacetCount> paymentMix) {

    public record SalesPoint(String day, long revenue, long orders) { }

    public record TopSeller(String productId, String name, long qty, long revenue) { }

    public long maxDailyRevenue() {
        return Math.max(1L, sales.stream().mapToLong(SalesPoint::revenue).max().orElse(1L));
    }
}
