package com.example.booking;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingTransactions {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CompensationRepository compensationRepository;

    public BookingTransactions(BookingRepository bookingRepository,
                               UserRepository userRepository,
                               CompensationRepository compensationRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.compensationRepository = compensationRepository;
    }

    // Отдельные транзакции нужны потому, что между ними есть HTTP-вызовы.
    @Transactional
    public Booking createPending(Long userId, LocalDate start, LocalDate end,
                                 String key, String requestData) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return bookingRepository.saveAndFlush(new Booking(user, start, end, key, requestData));
    }

    @Transactional
    public Booking setRoom(Long bookingId, Long roomId) {
        Booking booking = find(bookingId);
        if (booking.getStatus() != Booking.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Бронь уже обработана");
        }
        booking.setRoomId(roomId);
        return booking;
    }

    @Transactional
    public Booking confirm(Long bookingId) {
        Booking booking = find(bookingId);
        if (booking.getStatus() != Booking.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Бронь уже обработана");
        }
        booking.confirm();
        return booking;
    }

    @Transactional
    public Booking cancel(Long bookingId) {
        Booking booking = find(bookingId);
        booking.cancel();
        return booking;
    }

    @Transactional
    public void addCompensation(Long bookingId, Long roomId) {
        if (compensationRepository.findByBookingId(bookingId).isEmpty()) {
            compensationRepository.save(new CompensationTask(bookingId, roomId));
        }
    }

    @Transactional
    public void markCompensated(Long bookingId) {
        compensationRepository.findByBookingId(bookingId)
                .ifPresent(compensationRepository::delete);
    }

    @Transactional(readOnly = true)
    public Booking find(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Бронь не найдена"));
    }
}
