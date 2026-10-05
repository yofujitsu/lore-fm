package ru.yofujitsu.lorefm.release.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.yofujitsu.lorefm.release.application.InvalidSearchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidSearchException.class)
    ResponseEntity<ApiError> invalidSearch(InvalidSearchException ex) {
        return ResponseEntity.badRequest().body(new ApiError(ex.code(), ex.getMessage()));
    }
}
