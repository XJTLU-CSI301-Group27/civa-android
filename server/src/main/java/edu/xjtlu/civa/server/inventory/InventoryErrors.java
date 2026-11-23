package edu.xjtlu.civa.server.inventory;

import edu.xjtlu.civa.server.auth.ApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InventoryController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class InventoryErrors {
  @ExceptionHandler(InventoryFailure.class)
  ResponseEntity<Map<String, Object>> failure(InventoryFailure error, HttpServletRequest request) {
    int status =
        switch (error.code) {
          case "NOT_FOUND" -> 404;
          case "HOUSEHOLD_ACCESS_REVOKED", "HOUSEHOLD_REQUIRED" -> 403;
          case "VERSION_CONFLICT", "UNIT_CONFLICT", "INGREDIENT_INACTIVE", "NAME_CONFLICT" -> 409;
          default -> 500;
        };
    return ApiExceptionHandler.response(
        status, status == 500 ? "INTERNAL_ERROR" : error.code, request, new HttpHeaders());
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, Object>> database(
      DataAccessException error, HttpServletRequest request) {
    boolean retryable = error instanceof CannotAcquireLockException;
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState())) retryable = true;
    }
    if (!retryable)
      return ApiExceptionHandler.response(500, "INTERNAL_ERROR", request, new HttpHeaders());
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, "1");
    return ApiExceptionHandler.response(503, "TEMPORARILY_UNAVAILABLE", request, headers);
  }
}
