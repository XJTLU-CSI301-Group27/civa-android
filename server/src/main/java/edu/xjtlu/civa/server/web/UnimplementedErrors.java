package edu.xjtlu.civa.server.web;

import edu.xjtlu.civa.server.auth.ApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class UnimplementedErrors {
    @ExceptionHandler(UnimplementedController.NotImplemented.class)
    ResponseEntity<Map<String, Object>> notImplemented(HttpServletRequest request) {
        return ApiExceptionHandler.response(501, "CONTRACT_NOT_IMPLEMENTED", request, new HttpHeaders());
    }
}
