package com.example.meetingroom.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

public class CreateBookingRequest {

    private Long roomId;
    private String roomName;
    private String title;
    private String organiserEmail;
    private List<String> attendeeEmails;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime endTime;

    public CreateBookingRequest() {
    }

    public CreateBookingRequest(Long roomId, String title, String organiserEmail,
                                List<String> attendeeEmails, LocalDateTime startTime, LocalDateTime endTime) {
        this.roomId = roomId;
        this.title = title;
        this.organiserEmail = organiserEmail;
        this.attendeeEmails = attendeeEmails;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOrganiserEmail() {
        return organiserEmail;
    }

    public void setOrganiserEmail(String organiserEmail) {
        this.organiserEmail = organiserEmail;
    }

    public List<String> getAttendeeEmails() {
        return attendeeEmails;
    }

    public void setAttendeeEmails(List<String> attendeeEmails) {
        this.attendeeEmails = attendeeEmails;
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
}
