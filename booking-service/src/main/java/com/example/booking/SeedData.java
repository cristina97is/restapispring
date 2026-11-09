package com.example.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SeedData {

    @Bean
    CommandLineRunner addDemoUsers(UserRepository users,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.demo.admin-password}") String adminPassword,
                                   @Value("${app.demo.user-password}") String userPassword) {
        return args -> {
            if (users.findByUsername("admin").isEmpty()) {
                users.save(new User("admin", passwordEncoder.encode(adminPassword), Role.ADMIN));
            }
            if (users.findByUsername("demo").isEmpty()) {
                users.save(new User("demo", passwordEncoder.encode(userPassword), Role.USER));
            }
        };
    }
}
