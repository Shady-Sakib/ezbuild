package ezbuild.service;

/** Rule violation with a user-friendly message (409/400 style). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
