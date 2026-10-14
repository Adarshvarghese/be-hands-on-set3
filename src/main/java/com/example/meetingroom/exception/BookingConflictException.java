package com.example.meetingroom.exception;

import com.example.meetingroom.entity.Booking;

public class BookingConflictException extends RuntimeException {

    private final Booking clashingBooking;

    public BookingConflictException(Booking clashingBooking) {
        super(String.format("Booking overlaps with existing booking '%s' (ID: %d, %s to %s)",
                clashingBooking.getTitle(),
                clashingBooking.getId(),
                clashingBooking.getStartTime(),
                clashingBooking.getEndTime()));
        this.clashingBooking = clashingBooking;
    }

    public Booking getClashingBooking() {
        return clashingBooking;
    }
}
