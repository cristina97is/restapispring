package com.example.hotel;

import jakarta.persistence.*;

@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(columnNames = {"hotel_id", "number"}))
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "times_booked", nullable = false)
    private long timesBooked = 0;

    protected Room() { }

    public Room(Hotel hotel, String number, boolean available) {
        this.hotel = hotel;
        this.number = number;
        this.available = available;
    }

    public Long getId() { return id; }
    public Hotel getHotel() { return hotel; }
    public String getNumber() { return number; }
    public boolean isAvailable() { return available; }
    public long getTimesBooked() { return timesBooked; }

    public void setNumber(String number) { this.number = number; }
    public void setAvailable(boolean available) { this.available = available; }

    public void incrementTimesBooked() { timesBooked++; }
    public void decrementTimesBooked() { timesBooked = Math.max(0, timesBooked - 1); }
}
