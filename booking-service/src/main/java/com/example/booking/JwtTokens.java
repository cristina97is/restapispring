package com.example.booking;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class JwtTokens {

    private final JwtEncoder encoder;

    public JwtTokens(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String forUser(User user) {
        return create(user.getId().toString(), user.getRole().name(), Duration.ofHours(1));
    }

    public String forService() {
        return create("booking-service", "SERVICE", Duration.ofMinutes(5));
    }

    private String create(String subject, String role, Duration lifetime) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("hotel-booking")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .claim("roles", List.of(role))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
