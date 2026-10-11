package ezbuild.api;

import ezbuild.dto.ApiError;
import ezbuild.service.BusinessException;
import ezbuild.service.NotFoundException;
import ezbuild.service.OutOfStockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single JSON error shape for the API (status, error, message, path, timestamp, fieldErrors). Never leaks
 * stack traces.
 */
@Slf4j
@RestControllerAdvice(basePackages = "ezbuild.api")
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        e.getBindingResult().getGlobalErrors().forEach(g -> fields.putIfAbsent(g.getObjectName(), g.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(400, "Bad Request", "Validation failed.",
                req.getRequestURI(), Instant.now(), fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiError> unreadable(Exception e, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "The request body or parameters are not valid.", req);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<ApiError> stock(OutOfStockException e, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, e.getMessage(), req);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException e, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, e.getMessage(), req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> auth(AuthenticationException e, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException e, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, "You do not have permission for this action.", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
        log.error("Unexpected API error on {}", req.getRequestURI(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.", req);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), status.getReasonPhrase(), message,
                req.getRequestURI()));
    }
}
