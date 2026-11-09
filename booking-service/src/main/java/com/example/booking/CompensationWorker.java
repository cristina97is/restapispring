package com.example.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CompensationWorker {

    private static final Logger log = LoggerFactory.getLogger(CompensationWorker.class);

    private final HotelClient hotelClient;
    private final BookingTransactions transactions;
    private final CompensationRepository compensationRepository;

    public CompensationWorker(HotelClient hotelClient,
                              BookingTransactions transactions,
                              CompensationRepository compensationRepository) {
        this.hotelClient = hotelClient;
        this.transactions = transactions;
        this.compensationRepository = compensationRepository;
    }

    public void releaseOrKeepPending(long bookingId, long roomId) {
        try {
            hotelClient.release(roomId, bookingId);
            transactions.markCompensated(bookingId);
            log.info("bookingId={} compensation=DONE", bookingId);
        } catch (Exception ex) {
            // Попробуем ещё раз через 30 секунд.
            log.warn("bookingId={} compensation=WAITING reason={}", bookingId, ex.getMessage());
        }
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 30000)
    public void retryPending() {
        for (CompensationTask task : compensationRepository.findAll()) {
            releaseOrKeepPending(task.getBookingId(), task.getRoomId());
        }
    }
}
