package com.example.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import static com.example.booking.BookingDtos.*;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingTransactions transactions;
    private final BookingRepository bookingRepository;
    private final HotelClient hotelClient;
    private final CompensationWorker compensationWorker;

    public BookingService(BookingTransactions transactions,
                          BookingRepository bookingRepository,
                          HotelClient hotelClient,
                          CompensationWorker compensationWorker) {
        this.transactions = transactions;
        this.bookingRepository = bookingRepository;
        this.hotelClient = hotelClient;
        this.compensationWorker = compensationWorker;
    }

    public BookingResponse create(Long userId, BookingRequest request, String keyFromHeader) {
        checkDates(request.startDate(), request.endDate());

        if (!request.autoSelect() && request.roomId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажи roomId или autoSelect=true");
        }

        // Один ключ = один запрос. Это защищает от повторного создания брони.
        String key = keyFromHeader;
        if (key == null || key.isBlank()) {
            key = UUID.randomUUID().toString();
        }
        if (key.length() > 100 || !key.matches("[a-zA-Z0-9_:-]{1,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный Idempotency-Key");
        }

        // Сохраняем параметры запроса, чтобы ключ нельзя было использовать с другими датами.
        String requestData = userId + "|" + request.startDate() + "|" + request.endDate()
                + "|" + (request.autoSelect() ? null : request.roomId())
                + "|" + request.hotelId() + "|" + request.autoSelect();

        Booking oldBooking = bookingRepository.findByIdempotencyKey(key).orElse(null);
        if (oldBooking != null) {
            return getRepeatedRequest(oldBooking, userId, requestData);
        }

        Booking booking;
        try {
            booking = transactions.createPending(userId, request.startDate(), request.endDate(), key, requestData);
        } catch (DataIntegrityViolationException ex) {
            // Два запроса с одинаковым ключом могли прийти одновременно.
            Booking existing = bookingRepository.findByIdempotencyKey(key).orElseThrow(() -> ex);
            return getRepeatedRequest(existing, userId, requestData);
        }

        long bookingId = booking.getId();
        log.info("bookingId={} status=PENDING", bookingId);

        List<RoomView> rooms;
        try {
            if (request.autoSelect()) {
                rooms = hotelClient.recommend(request.startDate(), request.endDate(), request.hotelId());
            } else {
                rooms = List.of(new RoomView(request.roomId(), request.hotelId(), "", true, 0));
            }
        } catch (Exception ex) {
            transactions.cancel(bookingId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Не удалось получить номера, bookingId=" + bookingId, ex);
        }

        // Если авто-подбор включён, пробуем подходящие комнаты по очереди.
        for (RoomView room : rooms) {
            long roomId = room.id();
            transactions.setRoom(bookingId, roomId);

            try {
                // Шаг 1: номер временно занят в Hotel Service.
                hotelClient.reserve(roomId, bookingId, request.startDate(), request.endDate(), key);
            } catch (HttpClientErrorException.Conflict ex) {
                if (request.autoSelect()) {
                    continue;
                }
                transactions.cancel(bookingId);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Номер занят");
            } catch (Exception ex) {
                compensate(bookingId, roomId);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Ошибка связи с Hotel Service", ex);
            }

            try {
                // Шаг 2: окончательно подтверждаем номер и своё бронирование.
                hotelClient.commit(roomId, bookingId);
                Booking confirmed = transactions.confirm(bookingId);
                log.info("bookingId={} status=CONFIRMED roomId={}", bookingId, roomId);
                return BookingResponse.from(confirmed);
            } catch (Exception ex) {
                compensate(bookingId, roomId);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Не удалось подтвердить бронирование", ex);
            }
        }

        transactions.cancel(bookingId);
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Нет свободных номеров");
    }

    private void checkDates(LocalDate start, LocalDate end) {
        if (start == null || end == null || !start.isBefore(end)
                || start.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверные даты");
        }
    }

    private BookingResponse getRepeatedRequest(Booking booking, Long userId, String requestData) {
        if (!booking.getUser().getId().equals(userId)
                || !booking.getRequestFingerprint().equals(requestData)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Этот ключ уже использован для другого запроса");
        }
        return BookingResponse.from(booking);
    }

    private void compensate(long bookingId, long roomId) {
        // Если удалённый сервис упал, отменяем свою бронь и пытаемся освободить номер.
        transactions.cancel(bookingId);
        transactions.addCompensation(bookingId, roomId);
        compensationWorker.releaseOrKeepPending(bookingId, roomId);
        log.warn("bookingId={} status=CANCELLED compensation=REQUESTED", bookingId);
    }

    public BookingResponse cancel(Long userId, Long bookingId, boolean isAdmin) {
        Booking booking = transactions.find(bookingId);
        if (!isAdmin && !booking.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Бронирование не найдено");
        }

        Booking cancelled = transactions.cancel(bookingId);
        if (cancelled.getRoomId() != null) {
            transactions.addCompensation(bookingId, cancelled.getRoomId());
            compensationWorker.releaseOrKeepPending(bookingId, cancelled.getRoomId());
        }
        log.info("bookingId={} status=CANCELLED", bookingId);
        return BookingResponse.from(cancelled);
    }
}
