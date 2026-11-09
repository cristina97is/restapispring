package com.example.booking;

import java.time.Instant;
import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// DTO - это объекты для запросов и ответов API.
// Record позволяет не писать конструкторы и геттеры вручную.
public final class BookingDtos {

    private BookingDtos() { }

    public record BookingRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                 Long roomId, Long hotelId, boolean autoSelect) { }

    public record BookingResponse(Long id, Long userId, Long roomId,
                                  LocalDate startDate, LocalDate endDate,
                                  String status, Instant createdAt) {
        public static BookingResponse from(Booking booking) {
            return new BookingResponse(booking.getId(), booking.getUser().getId(),
                    booking.getRoomId(), booking.getStartDate(), booking.getEndDate(),
                    booking.getStatus().name(), booking.getCreatedAt());
        }
    }

    public record RoomView(Long id, Long hotelId, String number,
                           boolean available, long timesBooked) { }

    public record SlotRequest(Long bookingId, LocalDate startDate,
                              LocalDate endDate, String requestId) { }

    public record SlotAction(Long bookingId) { }

    public record Credentials(@NotBlank String username,
                              @NotBlank @Size(min = 8) String password) { }

    public record TokenResponse(String accessToken, String tokenType,
                                long expiresInSeconds) { }

    public record UserView(Long id, String username, String role) {
        public static UserView from(User user) {
            return new UserView(user.getId(), user.getUsername(), user.getRole().name());
        }
    }

    public record UserCreate(@NotBlank String username,
                             @NotBlank @Size(min = 8) String password,
                             @NotNull Role role) { }

    public record UserPatch(String username, String password, Role role) { }
}
