package com.example.meetingroom.controller;

import com.example.meetingroom.dto.BookingResponse;
import com.example.meetingroom.dto.RoomResponse;
import com.example.meetingroom.service.BookingService;
import com.example.meetingroom.service.RoomService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final BookingService bookingService;

    public RoomController(RoomService roomService, BookingService bookingService) {
        this.roomService = roomService;
        this.bookingService = bookingService;
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    @GetMapping("/{roomId}/bookings")
    public ResponseEntity<List<BookingResponse>> getBookingsForRoom(
            @PathVariable Long roomId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "includeCancelled", required = false, defaultValue = "false") boolean includeCancelled) {
        List<BookingResponse> bookings = bookingService.getBookingsForRoomAndDate(roomId, date, includeCancelled);
        return ResponseEntity.ok(bookings);
    }
}
