package edu.xjtlu.civa.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuthService.Failure.class)
    ResponseEntity<Map<String, Object>> authFailure(AuthService.Failure error, HttpServletRequest request) {
        int status = switch (error.code) {
            case "INVALID_CREDENTIALS" -> 401;
            case "USERNAME_TAKEN" -> 409;
            case "RATE_LIMITED" -> 429;
            default -> 500;
        };
        HttpHeaders headers = new HttpHeaders();
        if (status == 429) headers.set(HttpHeaders.RETRY_AFTER, Long.toString(error.retryAfterSeconds));
        return response(status, status == 500 ? "INTERNAL_ERROR" : error.code, request, headers);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, ConstraintViolationException.class,
            MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> invalidRequest(Exception error, HttpServletRequest request) {
        return response(400, "VALIDATION_ERROR", request, new HttpHeaders());
    }

    @ExceptionHandler(AuthController.NotImplemented.class)
    ResponseEntity<Map<String, Object>> notImplemented(HttpServletRequest request) {
        return response(501, "CONTRACT_NOT_IMPLEMENTED", request, new HttpHeaders());
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, Object>> databaseFailure(HttpServletRequest request) {
        return response(500, "INTERNAL_ERROR", request, new HttpHeaders());
    }

    public static ResponseEntity<Map<String, Object>> response(int status, String code, HttpServletRequest request, HttpHeaders headers) {
        Object trace = request.getAttribute(SecurityConfiguration.TRACE_ID);
        String traceId = trace instanceof String ? (String) trace : UUID.randomUUID().toString();
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_JSON).body(Map.of(
                "code", code, "message", status == 500 ? "Request failed" : code,
                "traceId", traceId, "fieldErrors", List.of()));
    }
}
