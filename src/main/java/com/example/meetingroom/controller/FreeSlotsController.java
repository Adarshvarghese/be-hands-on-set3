package com.example.meetingroom.controller;

import com.example.meetingroom.dto.FreeSlotResponse;
import com.example.meetingroom.dto.FreeSlotsRequest;
import com.example.meetingroom.service.BookingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/free-slots")
public class FreeSlotsController {

    private final BookingService bookingService;

    public FreeSlotsController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<List<FreeSlotResponse>> getFreeSlotsPost(@RequestBody FreeSlotsRequest request) {
        int duration = request.getMinDurationMinutes() != null ? request.getMinDurationMinutes() : 15;
        List<FreeSlotResponse> slots = bookingService.findCommonFreeSlots(
                request.getDate(),
                duration,
                request.getAttendeeEmails()
        );
        return ResponseEntity.ok(slots);
    }

    @GetMapping
    public ResponseEntity<List<FreeSlotResponse>> getFreeSlotsGet(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "duration", required = false, defaultValue = "15") int duration,
            @RequestParam(name = "attendees", required = false) List<String> attendees,
            @RequestParam(name = "attendeeEmails", required = false) List<String> attendeeEmails) {

        List<String> emails = attendees != null && !attendees.isEmpty() ? attendees : attendeeEmails;
        List<FreeSlotResponse> slots = bookingService.findCommonFreeSlots(date, duration, emails);
        return ResponseEntity.ok(slots);
    }
}
