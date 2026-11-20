package edu.xjtlu.civa.server.planning;

import edu.xjtlu.civa.server.auth.ApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class BdErrors {
  @ExceptionHandler(BdRules.Failure.class)
  ResponseEntity<Map<String, Object>> failure(BdRules.Failure error, HttpServletRequest request) {
    int status =
        switch (error.code) {
          case "NOT_FOUND" -> 404;
          case "HOUSEHOLD_ACCESS_REVOKED" -> 403;
          case "VERSION_CONFLICT", "PLAN_LOCKED" -> 409;
          case "CONTRACT_NOT_IMPLEMENTED" -> 501;
          default -> 400;
        };
    return ApiExceptionHandler.response(status, error.code, request, new HttpHeaders());
  }
}
