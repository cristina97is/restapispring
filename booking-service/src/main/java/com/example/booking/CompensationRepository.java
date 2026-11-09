package com.example.booking;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompensationRepository extends JpaRepository<CompensationTask, Long> {

    Optional<CompensationTask> findByBookingId(Long bookingId);
}
