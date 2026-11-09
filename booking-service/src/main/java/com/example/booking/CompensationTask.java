package com.example.booking;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "compensation_tasks")
public class CompensationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", unique = true, nullable = false)
    private Long bookingId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CompensationTask() { }

    public CompensationTask(Long bookingId, Long roomId) {
        this.bookingId = bookingId;
        this.roomId = roomId;
        this.createdAt = Instant.now();
    }

    public Long getBookingId() { return bookingId; }
    public Long getRoomId() { return roomId; }
}
