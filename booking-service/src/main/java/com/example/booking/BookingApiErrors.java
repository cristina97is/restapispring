package com.example.booking;

import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class BookingApiErrors {

    public record ErrorDto(Instant timestamp, int status, String message, String traceId) { }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorDto> handleStatus(ResponseStatusException exception) {
        int code = exception.getStatusCode().value();
        return ResponseEntity.status(code)
                .body(new ErrorDto(Instant.now(), code, exception.getReason(), MDC.get("traceId")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorDto(Instant.now(), 400, "Некорректные поля запроса", MDC.get("traceId")));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDto> handleDuplicate(Exception exception) {
        return ResponseEntity.status(409)
                .body(new ErrorDto(Instant.now(), 409, "Запись уже существует", MDC.get("traceId")));
    }
}
