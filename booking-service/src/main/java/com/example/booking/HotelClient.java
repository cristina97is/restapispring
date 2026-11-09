package com.example.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import static com.example.booking.BookingDtos.*;

@Component
public class HotelClient {

    private final RestClient client;
    private final JwtTokens jwtTokens;

    public HotelClient(RestClient.Builder builder, JwtTokens jwtTokens) {
        this.client = builder.baseUrl("http://hotel-service").build();
        this.jwtTokens = jwtTokens;
    }

    // Во всех удалённых запросах есть Retry и CircuitBreaker.
    // Ограничения и интервалы повторов настроены в application.yml.
    @Retry(name = "hotelClient")
    @CircuitBreaker(name = "hotelClient")
    public List<RoomView> recommend(LocalDate start, LocalDate end, Long hotelId) {
        List<RoomView> rooms = client.get()
                .uri(builder -> {
                    builder.path("/api/rooms/recommend");
                    builder.queryParam("startDate", start);
                    builder.queryParam("endDate", end);
                    if (hotelId != null) {
                        builder.queryParam("hotelId", hotelId);
                    }
                    return builder.build();
                })
                .headers(headers -> setHeaders(headers))
                .retrieve()
                .body(new ParameterizedTypeReference<List<RoomView>>() {});
        return rooms == null ? List.of() : rooms;
    }

    @Retry(name = "hotelClient")
    @CircuitBreaker(name = "hotelClient")
    public void reserve(long roomId, long bookingId, LocalDate start,
                        LocalDate end, String requestId) {
        send(roomId, "confirm-availability", new SlotRequest(bookingId, start, end, requestId));
    }

    @Retry(name = "hotelClient")
    @CircuitBreaker(name = "hotelClient")
    public void commit(long roomId, long bookingId) {
        send(roomId, "commit", new SlotAction(bookingId));
    }

    @Retry(name = "hotelClient")
    @CircuitBreaker(name = "hotelClient")
    public void release(long roomId, long bookingId) {
        send(roomId, "release", new SlotAction(bookingId));
    }

    private void send(long roomId, String action, Object body) {
        client.post()
                .uri("/api/rooms/{id}/{action}", roomId, action)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> setHeaders(headers))
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void setHeaders(org.springframework.http.HttpHeaders headers) {
        headers.setBearerAuth(jwtTokens.forService());
        String trace = MDC.get("traceId");
        headers.set("X-Trace-Id", trace != null ? trace : UUID.randomUUID().toString());
    }
}
