package com.example.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import static com.example.booking.BookingDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class BookingFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private BookingRepository bookings;
    @Autowired private JwtTokens jwtTokens;
    @Autowired private CompensationRepository compensationTasks;
    @Autowired private CompensationWorker compensationWorker;
    @MockBean private HotelClient hotelClient;

    private String token;
    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setup() {
        User user = users.findByUsername("demo").orElseThrow();
        token = jwtTokens.forUser(user);
        start = LocalDate.now().plusDays(30);
        end = start.plusDays(2);
    }

    private String body(long roomId) {
        return "{\"startDate\":\"" + start + "\",\"endDate\":\"" + end
                + "\",\"roomId\":" + roomId + ",\"autoSelect\":false}";
    }

    // Одинаковый ключ запроса не должен создавать вторую бронь.
    @Test
    void repeatRequestDoesNotMakeDuplicate() throws Exception {
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(501)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(501)))
                .andExpect(status().isCreated());

        verify(hotelClient, times(1)).reserve(eq(501L), anyLong(), any(), any(), eq(key));
        assertThat(bookings.findByIdempotencyKey(key).orElseThrow().getStatus())
                .isEqualTo(Booking.Status.CONFIRMED);
    }

    // Если Hotel Service не подтвердил номер, бронь отменяется.
    @Test
    void failedCommitCancelsBooking() throws Exception {
        doThrow(new RestClientException("test timeout"))
                .when(hotelClient).commit(eq(502L), anyLong());
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(502)))
                .andExpect(status().isServiceUnavailable());

        assertThat(bookings.findByIdempotencyKey(key).orElseThrow().getStatus())
                .isEqualTo(Booking.Status.CANCELLED);
        verify(hotelClient).release(eq(502L), anyLong());
    }

    @Test
    void occupiedRoomReturns409() throws Exception {
        HttpClientErrorException conflict = HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Busy", HttpHeaders.EMPTY, new byte[0], null);
        doThrow(conflict).when(hotelClient).reserve(eq(503L), anyLong(), any(), any(), any());
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(503)))
                .andExpect(status().isConflict());

        assertThat(bookings.findByIdempotencyKey(key).orElseThrow().getStatus())
                .isEqualTo(Booking.Status.CANCELLED);
    }

    @Test
    void autoSelectTakesFirstRecommendedRoom() throws Exception {
        when(hotelClient.recommend(eq(start), eq(end), isNull()))
                .thenReturn(List.of(
                        new RoomView(11L, 1L, "101", true, 0),
                        new RoomView(12L, 1L, "102", true, 1)));
        String request = "{\"startDate\":\"" + start + "\",\"endDate\":\""
                + end + "\",\"autoSelect\":true}";

        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(11));
    }

    @Test
    void noTokenAndWrongDatesAreRejected() throws Exception {
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(42)))
                .andExpect(status().isUnauthorized());

        String wrongDates = "{\"startDate\":\"" + end + "\",\"endDate\":\""
                + start + "\",\"roomId\":42}";
        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongDates))
                .andExpect(status().isBadRequest());
    }

    @Test
    void compensationCanBeRetried() throws Exception {
        doThrow(new RestClientException("commit unavailable"))
                .when(hotelClient).commit(eq(509L), anyLong());
        doThrow(new RestClientException("release unavailable"))
                .when(hotelClient).release(eq(509L), anyLong());
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(509)))
                .andExpect(status().isServiceUnavailable());

        long bookingId = bookings.findByIdempotencyKey(key).orElseThrow().getId();
        assertThat(compensationTasks.findByBookingId(bookingId)).isPresent();

        reset(hotelClient);
        compensationWorker.retryPending();
        assertThat(compensationTasks.findByBookingId(bookingId)).isEmpty();
    }

    @Test
    void regularUserCannotSeeAllUsers() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
