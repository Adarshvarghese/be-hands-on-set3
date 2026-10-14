package com.example.meetingroom.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public class FreeSlotsRequest {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @JsonAlias({"duration", "minDuration", "durationMinutes"})
    private Integer minDurationMinutes;

    @JsonAlias({"attendees"})
    private List<String> attendeeEmails;

    public FreeSlotsRequest() {
    }

    public FreeSlotsRequest(LocalDate date, Integer minDurationMinutes, List<String> attendeeEmails) {
        this.date = date;
        this.minDurationMinutes = minDurationMinutes;
        this.attendeeEmails = attendeeEmails;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getMinDurationMinutes() {
        return minDurationMinutes;
    }

    public void setMinDurationMinutes(Integer minDurationMinutes) {
        this.minDurationMinutes = minDurationMinutes;
    }

    public List<String> getAttendeeEmails() {
        return attendeeEmails;
    }

    public void setAttendeeEmails(List<String> attendeeEmails) {
        this.attendeeEmails = attendeeEmails;
    }
}
