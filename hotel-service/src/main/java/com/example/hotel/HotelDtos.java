package com.example.hotel;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class HotelDtos {

    private HotelDtos() { }

    public record HotelIn(@NotBlank String name, @NotBlank String address) { }

    public record HotelOut(Long id, String name, String address) {
        public static HotelOut from(Hotel hotel) {
            return new HotelOut(hotel.getId(), hotel.getName(), hotel.getAddress());
        }
    }

    public record RoomIn(@NotNull Long hotelId, @NotBlank String number, Boolean available) { }

    public record RoomPatch(String number, Boolean available) { }

    public record RoomOut(Long id, Long hotelId, String number,
                          boolean available, long timesBooked) {
        public static RoomOut from(Room room) {
            return new RoomOut(room.getId(), room.getHotel().getId(),
                    room.getNumber(), room.isAvailable(), room.getTimesBooked());
        }
    }

    public record SlotRequest(@NotNull Long bookingId,
                              @NotNull LocalDate startDate,
                              @NotNull LocalDate endDate,
                              @NotBlank String requestId) { }

    public record SlotAction(@NotNull Long bookingId) { }

    public record Stats(Long roomId, long timesBooked, long activeReservations) { }
}
