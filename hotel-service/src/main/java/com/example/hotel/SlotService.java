package com.example.hotel;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.example.hotel.HotelDtos.*;

@Service
public class SlotService {

    private static final Logger log = LoggerFactory.getLogger(SlotService.class);

    private final RoomRepository roomRepository;
    private final SlotRepository slotRepository;

    public SlotService(RoomRepository roomRepository, SlotRepository slotRepository) {
        this.roomRepository = roomRepository;
        this.slotRepository = slotRepository;
    }

    @Transactional
    public void reserve(long roomId, SlotRequest request) {
        HotelService.validateDates(request.startDate(), request.endDate());

        // Блокируем строку комнаты в БД. Два потока не смогут занять её одновременно.
        Room room = roomRepository.lockById(roomId)
                .orElseThrow(() -> HotelService.notFound("Номер"));

        RoomSlot existing = slotRepository.findByBookingId(request.bookingId()).orElse(null);
        if (existing != null) {
            boolean sameRoom = Objects.equals(existing.getRoom().getId(), roomId);
            boolean sameDates = existing.getStartDate().equals(request.startDate())
                    && existing.getEndDate().equals(request.endDate());
            if (sameRoom && sameDates) {
                return; // повторный запрос ничего не меняет
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Эта бронь уже существует");
        }

        if (!room.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Номер недоступен");
        }
        long overlaps = slotRepository.countOverlaps(roomId, request.startDate(), request.endDate());
        if (overlaps > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Номер занят на эти даты");
        }

        RoomSlot slot = new RoomSlot(request.bookingId(), room,
                request.startDate(), request.endDate(), request.requestId());
        slotRepository.saveAndFlush(slot);
        log.info("bookingId={} roomId={} step=HELD", request.bookingId(), roomId);
    }

    @Transactional
    public void commit(long roomId, SlotAction request) {
        Room room = roomRepository.lockById(roomId)
                .orElseThrow(() -> HotelService.notFound("Номер"));
        RoomSlot slot = slotRepository.findByBookingId(request.bookingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Нет временной брони"));

        if (!slot.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Неверный номер");
        }
        if (slot.getState() == RoomSlot.State.CONFIRMED) {
            return; // повторное подтверждение не увеличивает счётчик
        }

        slot.confirm();
        room.incrementTimesBooked();
        log.info("bookingId={} roomId={} step=CONFIRMED", request.bookingId(), roomId);
    }

    @Transactional
    public void release(long roomId, SlotAction request) {
        Room room = roomRepository.lockById(roomId)
                .orElseThrow(() -> HotelService.notFound("Номер"));
        RoomSlot slot = slotRepository.findByBookingId(request.bookingId()).orElse(null);
        if (slot == null) {
            return; // номер уже освобождён
        }
        if (!slot.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Неверный номер");
        }

        if (slot.getState() == RoomSlot.State.CONFIRMED) {
            room.decrementTimesBooked();
        }
        slotRepository.delete(slot);
        log.info("bookingId={} roomId={} step=RELEASED", request.bookingId(), roomId);
    }
}
