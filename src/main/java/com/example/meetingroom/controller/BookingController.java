package com.example.meetingroom.controller;

import com.example.meetingroom.dto.BookingResponse;
import com.example.meetingroom.dto.CreateBookingRequest;
import com.example.meetingroom.service.BookingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@RequestBody CreateBookingRequest request) {
        BookingResponse created = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long id) {
        BookingResponse booking = bookingService.getBooking(id);
        return ResponseEntity.ok(booking);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable Long id) {
        BookingResponse cancelled = bookingService.cancelBooking(id);
        return ResponseEntity.ok(cancelled);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBookingViaPost(@PathVariable Long id) {
        BookingResponse cancelled = bookingService.cancelBooking(id);
        return ResponseEntity.ok(cancelled);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBookingViaPatch(@PathVariable Long id) {
        BookingResponse cancelled = bookingService.cancelBooking(id);
        return ResponseEntity.ok(cancelled);
    }

    @GetMapping
    public ResponseEntity<List<BookingResponse>> getBookings(
            @RequestParam(name = "roomId") Long roomId,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "includeCancelled", required = false, defaultValue = "false") boolean includeCancelled) {
        List<BookingResponse> bookings = bookingService.getBookingsForRoomAndDate(roomId, date, includeCancelled);
        return ResponseEntity.ok(bookings);
    }
}
