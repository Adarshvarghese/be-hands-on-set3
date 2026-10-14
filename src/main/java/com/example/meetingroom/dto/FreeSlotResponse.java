package com.example.meetingroom.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FreeSlotResponse {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;

    private long durationMinutes;
    private List<RoomResponse> availableRooms = new ArrayList<>();

    public FreeSlotResponse() {
    }

    public FreeSlotResponse(LocalDateTime startTime, LocalDateTime endTime, long durationMinutes, List<RoomResponse> availableRooms) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = durationMinutes;
        this.availableRooms = availableRooms != null ? availableRooms : new ArrayList<>();
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public long getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(long durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public List<RoomResponse> getAvailableRooms() {
        return availableRooms;
    }

    public void setAvailableRooms(List<RoomResponse> availableRooms) {
        this.availableRooms = availableRooms;
    }
}
