package com.example.booking;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.example.booking.BookingDtos.*;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping({"/api/auth/register", "/user/register"})
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody Credentials data) {
        return ResponseEntity.status(201).body(authService.register(data));
    }

    @PostMapping({"/api/auth/login", "/user/auth"})
    public TokenResponse login(@Valid @RequestBody Credentials data) {
        return authService.login(data);
    }
}
