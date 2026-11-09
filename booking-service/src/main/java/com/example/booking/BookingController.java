package com.example.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

import static com.example.booking.BookingDtos.*;

@RestController
public class BookingController {

    private final BookingService bookingService;
    private final BookingTransactions transactions;
    private final BookingRepository bookingRepository;

    public BookingController(BookingService bookingService,
                             BookingTransactions transactions,
                             BookingRepository bookingRepository) {
        this.bookingService = bookingService;
        this.transactions = transactions;
        this.bookingRepository = bookingRepository;
    }

    @PostMapping({"/api/bookings", "/booking"})
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            JwtAuthenticationToken auth) {
        BookingResponse booking = bookingService.create(getUserId(auth), request, key);
        return ResponseEntity.status(201).body(booking);
    }

    @GetMapping({"/api/bookings", "/bookings"})
    public Page<BookingResponse> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken auth) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверная страница");
        }
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(getUserId(auth),
                PageRequest.of(page, size)).map(BookingResponse::from);
    }

    @GetMapping({"/api/bookings/{id}", "/booking/{id}"})
    public BookingResponse getOne(@PathVariable long id, JwtAuthenticationToken auth) {
        Booking booking = transactions.find(id);
        if (!isAdmin(auth) && !booking.getUser().getId().equals(getUserId(auth))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Бронь не найдена");
        }
        return BookingResponse.from(booking);
    }

    @DeleteMapping({"/api/bookings/{id}", "/booking/{id}"})
    public BookingResponse cancel(@PathVariable long id, JwtAuthenticationToken auth) {
        return bookingService.cancel(getUserId(auth), id, isAdmin(auth));
    }

    private long getUserId(JwtAuthenticationToken auth) {
        try {
            return Long.parseLong(auth.getToken().getSubject());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Токен не принадлежит пользователю");
        }
    }

    private boolean isAdmin(JwtAuthenticationToken auth) {
        return auth.getAuthorities().stream()
                .anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"));
    }
}
