package ezbuild.dto;

import java.util.List;

public record CartView(List<CartLine> lines, long subtotal, long savings, int count) {

    public static CartView of(List<CartLine> lines) {
        long subtotal = lines.stream().mapToLong(CartLine::lineTotal).sum();
        long savings = lines.stream().mapToLong(CartLine::lineSaving).sum();
        int count = lines.stream().mapToInt(CartLine::qty).sum();
        return new CartView(lines, subtotal, savings, count);
    }

    public boolean empty() {
        return lines.isEmpty();
    }

    public boolean hasStockProblem() {
        return lines.stream().anyMatch(CartLine::overStock);
    }
}
