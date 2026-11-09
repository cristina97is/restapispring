package com.example.booking;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static com.example.booking.BookingDtos.*;

@RestController
@RequestMapping({"/api/users", "/user"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsersController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUsersController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<UserView> getUsers() {
        return userRepository.findAll().stream().map(UserView::from).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<UserView> create(@Valid @RequestBody UserCreate data) {
        if (userRepository.existsByUsername(data.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Такой пользователь уже есть");
        }
        User user = new User(data.username(), passwordEncoder.encode(data.password()), data.role());
        userRepository.save(user);
        return ResponseEntity.status(201).body(UserView.from(user));
    }

    @PatchMapping("/{id}")
    @Transactional
    public UserView edit(@PathVariable long id, @RequestBody UserPatch data) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
        if (data.username() != null && !data.username().isBlank()) {
            user.setUsername(data.username());
        }
        if (data.password() != null) {
            if (data.password().length() < 8) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Слишком короткий пароль");
            }
            user.setPassword(passwordEncoder.encode(data.password()));
        }
        if (data.role() != null) {
            user.setRole(data.role());
        }
        return UserView.from(user);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
        }
        userRepository.deleteById(id);
    }
}
