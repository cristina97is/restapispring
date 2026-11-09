package com.example.hotel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.example.hotel.HotelDtos.*;

@Service
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final SlotRepository slotRepository;

    public HotelService(HotelRepository hotelRepository,
                        RoomRepository roomRepository,
                        SlotRepository slotRepository) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.slotRepository = slotRepository;
    }

    @Transactional(readOnly = true)
    public List<HotelOut> hotels() {
        List<HotelOut> result = new ArrayList<>();
        for (Hotel hotel : hotelRepository.findAll()) {
            result.add(HotelOut.from(hotel));
        }
        return result;
    }

    @Transactional
    public HotelOut createHotel(HotelIn data) {
        Hotel hotel = new Hotel(data.name(), data.address());
        return HotelOut.from(hotelRepository.save(hotel));
    }

    @Transactional
    public HotelOut editHotel(long id, HotelIn data) {
        Hotel hotel = hotelRepository.findById(id).orElseThrow(() -> notFound("Отель"));
        hotel.setName(data.name());
        hotel.setAddress(data.address());
        return HotelOut.from(hotel);
    }

    @Transactional
    public void removeHotel(long id) {
        if (!hotelRepository.existsById(id)) {
            throw notFound("Отель");
        }
        if (!roomRepository.findByHotelId(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Сначала удали все номера этого отеля");
        }
        hotelRepository.deleteById(id);
    }

    @Transactional
    public RoomOut createRoom(RoomIn data) {
        Hotel hotel = hotelRepository.findById(data.hotelId())
                .orElseThrow(() -> notFound("Отель"));
        boolean available = data.available() == null || data.available();
        Room room = new Room(hotel, data.number(), available);
        return RoomOut.from(roomRepository.save(room));
    }

    @Transactional
    public RoomOut editRoom(long id, RoomPatch data) {
        Room room = roomRepository.lockById(id).orElseThrow(() -> notFound("Номер"));
        if (data.number() != null && !data.number().isBlank()) {
            room.setNumber(data.number());
        }
        if (data.available() != null) {
            room.setAvailable(data.available());
        }
        return RoomOut.from(room);
    }

    @Transactional
    public void removeRoom(long id) {
        Room room = roomRepository.lockById(id).orElseThrow(() -> notFound("Номер"));
        if (slotRepository.existsByRoomId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Есть активные бронирования");
        }
        roomRepository.delete(room);
    }

    @Transactional(readOnly = true)
    public List<RoomOut> listAvailable(LocalDate start, LocalDate end,
                                       Long hotelId, boolean sortByPopularity) {
        validateDates(start, end);
        List<Room> rooms = roomRepository.availableRooms(start, end, hotelId);

        // Реализация алгоритма из ТЗ: сначала рекомендуем редко бронируемые номера.
        if (sortByPopularity) {
            rooms.sort(Comparator.comparingLong(Room::getTimesBooked)
                    .thenComparing(Room::getId));
        }

        List<RoomOut> result = new ArrayList<>();
        for (Room room : rooms) {
            result.add(RoomOut.from(room));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<RoomOut> allRooms() {
        List<RoomOut> result = new ArrayList<>();
        for (Room room : roomRepository.findAll()) {
            result.add(RoomOut.from(room));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Stats> stats() {
        List<Stats> result = new ArrayList<>();
        for (Room room : roomRepository.findAll()) {
            long active = slotRepository.countByRoomId(room.getId());
            result.add(new Stats(room.getId(), room.getTimesBooked(), active));
        }
        return result;
    }

    public static void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null || !start.isBefore(end)
                || start.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверные даты");
        }
    }

    public static ResponseStatusException notFound(String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, name + " не найден");
    }
}
