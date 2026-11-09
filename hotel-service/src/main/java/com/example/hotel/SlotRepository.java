package com.example.hotel;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<RoomSlot, Long> {

    Optional<RoomSlot> findByBookingId(Long bookingId);

    boolean existsByRoomId(Long roomId);

    long countByRoomId(Long roomId);

    @Query("""
            select count(s) from RoomSlot s
            where s.room.id = :roomId
              and s.startDate < :endDate
              and s.endDate > :startDate
            """)
    long countOverlaps(@Param("roomId") Long roomId,
                       @Param("startDate") LocalDate startDate,
                       @Param("endDate") LocalDate endDate);
}
