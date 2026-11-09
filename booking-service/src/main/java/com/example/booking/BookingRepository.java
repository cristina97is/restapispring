package com.example.booking;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String key);

    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
