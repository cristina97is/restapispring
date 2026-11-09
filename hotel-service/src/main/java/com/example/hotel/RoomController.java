package com.example.hotel;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.example.hotel.HotelDtos.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final HotelService hotelService;
    private final SlotService slotService;

    public RoomController(HotelService hotelService, SlotService slotService) {
        this.hotelService = hotelService;
        this.slotService = slotService;
    }

    @GetMapping
    public List<RoomOut> freeRooms(@RequestParam LocalDate startDate,
                                   @RequestParam LocalDate endDate,
                                   @RequestParam(required = false) Long hotelId) {
        return hotelService.listAvailable(startDate, endDate, hotelId, false);
    }

    @GetMapping("/recommend")
    public List<RoomOut> recommended(@RequestParam LocalDate startDate,
                                     @RequestParam LocalDate endDate,
                                     @RequestParam(required = false) Long hotelId) {
        return hotelService.listAvailable(startDate, endDate, hotelId, true);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RoomOut> all() {
        return hotelService.allRooms();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Stats> stats() {
        return hotelService.stats();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomOut> add(@Valid @RequestBody RoomIn data) {
        return ResponseEntity.status(201).body(hotelService.createRoom(data));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomOut edit(@PathVariable long id, @RequestBody RoomPatch data) {
        return hotelService.editRoom(id, data);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        hotelService.removeRoom(id);
    }

    // Следующие три метода доступны только другому микросервису, а не пользователю.
    @PostMapping("/{id}/confirm-availability")
    @PreAuthorize("hasRole('SERVICE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserve(@PathVariable long id, @Valid @RequestBody SlotRequest data) {
        slotService.reserve(id, data);
    }

    @PostMapping("/{id}/commit")
    @PreAuthorize("hasRole('SERVICE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void commit(@PathVariable long id, @Valid @RequestBody SlotAction data) {
        slotService.commit(id, data);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasRole('SERVICE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable long id, @Valid @RequestBody SlotAction data) {
        slotService.release(id, data);
    }
}
