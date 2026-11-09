package com.example.hotel;

import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.web.server.ResponseStatusException;

import static com.example.hotel.HotelDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "eureka.client.enabled=false")
class SlotServiceIntegrationTest {

    private static final AtomicInteger ids = new AtomicInteger(100000);

    @Autowired private SlotService slotService;
    @Autowired private HotelService hotelService;
    @Autowired private HotelRepository hotels;
    @Autowired private RoomRepository rooms;
    @Autowired private SlotRepository slots;

    private Room room;
    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setup() {
        start = LocalDate.now().plusDays(20);
        end = start.plusDays(3);
        Hotel hotel = hotels.save(new Hotel("Test " + ids.incrementAndGet(), "Test address"));
        room = rooms.save(new Room(hotel, "TEST", true));
    }

    @Test
    void repeatedReserveAndCommitDoNotChangeResult() {
        long bookingId = ids.incrementAndGet();
        SlotRequest request = new SlotRequest(bookingId, start, end, "test-" + bookingId);

        slotService.reserve(room.getId(), request);
        slotService.reserve(room.getId(), request);
        assertThat(slots.countOverlaps(room.getId(), start, end)).isEqualTo(1L);

        slotService.commit(room.getId(), new SlotAction(bookingId));
        slotService.commit(room.getId(), new SlotAction(bookingId));
        assertThat(rooms.findById(room.getId()).orElseThrow().getTimesBooked()).isEqualTo(1L);

        slotService.release(room.getId(), new SlotAction(bookingId));
        slotService.release(room.getId(), new SlotAction(bookingId));
        assertThat(slots.countOverlaps(room.getId(), start, end)).isZero();
    }

    @Test
    void dateOverlapsAreRejected() {
        long first = ids.incrementAndGet();
        long second = ids.incrementAndGet();
        long third = ids.incrementAndGet();

        slotService.reserve(room.getId(), new SlotRequest(first, start, end, "first"));
        assertThrows(ResponseStatusException.class, () -> slotService.reserve(room.getId(),
                new SlotRequest(second, start.plusDays(1), end.plusDays(1), "second")));

        // Дата выезда не считается занятой: следующая бронь может начаться в этот день.
        slotService.reserve(room.getId(), new SlotRequest(third, end, end.plusDays(1), "third"));
        assertThat(slots.countOverlaps(room.getId(), start, end.plusDays(1))).isEqualTo(2L);
    }

    @Test
    void recommendationsChooseLessPopularRoom() {
        Hotel hotel = hotels.save(new Hotel("Other " + ids.incrementAndGet(), "Address"));
        Room popular = rooms.save(new Room(hotel, "A", true));
        Room newRoom = rooms.save(new Room(hotel, "B", true));

        long bookingId = ids.incrementAndGet();
        LocalDate earlier = LocalDate.now().plusDays(5);
        slotService.reserve(popular.getId(),
                new SlotRequest(bookingId, earlier, earlier.plusDays(1), "stats"));
        slotService.commit(popular.getId(), new SlotAction(bookingId));

        var result = hotelService.listAvailable(start, end, hotel.getId(), true);
        assertThat(result).extracting(HotelDtos.RoomOut::id)
                .containsExactly(newRoom.getId(), popular.getId());
    }

    @Test
    void parallelRequestsCannotBookSameRoom() throws Exception {
        long first = ids.incrementAndGet();
        long second = ids.incrementAndGet();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startSignal = new CountDownLatch(1);

        try {
            Future<Boolean> one = executor.submit(() -> tryReserve(startSignal, first));
            Future<Boolean> two = executor.submit(() -> tryReserve(startSignal, second));
            startSignal.countDown();

            int success = (one.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (two.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(success).isEqualTo(1);
            assertThat(slots.countOverlaps(room.getId(), start, end)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean tryReserve(CountDownLatch signal, long bookingId) throws InterruptedException {
        signal.await();
        try {
            slotService.reserve(room.getId(),
                    new SlotRequest(bookingId, start, end, "race-" + bookingId));
            return true;
        } catch (ResponseStatusException | CannotAcquireLockException ex) {
            return false;
        }
    }
}
