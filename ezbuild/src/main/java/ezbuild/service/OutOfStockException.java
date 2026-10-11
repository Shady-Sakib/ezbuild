package ezbuild.service;

public class OutOfStockException extends BusinessException {
    public OutOfStockException(String productName) {
        super("Sorry, \"" + productName + "\" does not have enough stock left.");
    }
}
