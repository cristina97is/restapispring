package com.example.hotel;

import java.time.LocalDate;
import jakarta.persistence.*;

// Хранит занятые даты. Поле Room.available означает «не на ремонте».
@Entity
@Table(name = "room_slots", indexes = {
        @Index(name = "idx_room_slot_dates", columnList = "room_id,start_date,end_date")
})
public class RoomSlot {

    public enum State { HELD, CONFIRMED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    protected RoomSlot() { }

    public RoomSlot(Long bookingId, Room room, LocalDate startDate,
                    LocalDate endDate, String requestId) {
        this.bookingId = bookingId;
        this.room = room;
        this.startDate = startDate;
        this.endDate = endDate;
        this.requestId = requestId;
        this.state = State.HELD;
    }

    public Long getBookingId() { return bookingId; }
    public Room getRoom() { return room; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public State getState() { return state; }

    public void confirm() { state = State.CONFIRMED; }
}
