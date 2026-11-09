package com.example.gateway;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class GatewaySecurity {

    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${app.security.jwt-secret-base64}") String encoded) {
        byte[] data = Base64.getDecoder().decode(encoded);
        if (data.length < 32) {
            throw new IllegalStateException("JWT secret is too short");
        }
        SecretKey secretKey = new SecretKeySpec(data, "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("hotel-booking"));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/**", "/user/register", "/user/auth").permitAll()
                        // Технические методы не должны попадать в Gateway.
                        .pathMatchers("/api/rooms/*/confirm-availability",
                                "/api/rooms/*/commit", "/api/rooms/*/release").denyAll()
                        .pathMatchers(HttpMethod.POST, "/api/hotels/**", "/api/rooms").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/hotels/**", "/api/rooms/**",
                                "/api/users/**", "/user/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/hotels/**", "/api/rooms/**",
                                "/api/users/**", "/user/**").hasRole("ADMIN")
                        .pathMatchers("/api/users/**", "/user/**").hasRole("ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter))))
                .build();
    }
}
