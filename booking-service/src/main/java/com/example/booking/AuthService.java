package com.example.booking;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.example.booking.BookingDtos.*;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokens jwtTokens;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokens jwtTokens) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokens = jwtTokens;
    }

    @Transactional
    public TokenResponse register(Credentials data) {
        if (userRepository.existsByUsername(data.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Такой пользователь уже есть");
        }

        // В базе хранится хеш пароля, а не сам пароль.
        String passwordHash = passwordEncoder.encode(data.password());
        User user = new User(data.username(), passwordHash, Role.USER);
        userRepository.save(user);

        return new TokenResponse(jwtTokens.forUser(user), "Bearer", 3600);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(Credentials data) {
        User user = userRepository.findByUsername(data.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль"));

        if (!passwordEncoder.matches(data.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
        }
        return new TokenResponse(jwtTokens.forUser(user), "Bearer", 3600);
    }
}
