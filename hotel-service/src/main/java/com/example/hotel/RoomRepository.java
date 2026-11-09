package com.example.hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

    // Защита от конкурентного бронирования.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> lockById(@Param("id") Long id);

    // Два интервала пересекаются, если startA < endB и endA > startB.
    @Query("""
            select r from Room r
            where r.available = true
              and (:hotelId is null or r.hotel.id = :hotelId)
              and r.id not in (
                select s.room.id from RoomSlot s
                where s.startDate < :endDate and s.endDate > :startDate
              )
            """)
    List<Room> availableRooms(@Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate,
                              @Param("hotelId") Long hotelId);

    List<Room> findByHotelId(Long hotelId);
}
