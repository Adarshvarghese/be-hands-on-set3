package com.example.meetingroom.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private String title;

    @Column(name = "organiser_email", nullable = false)
    private String organiserEmail;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_attendees", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "attendee_email", nullable = false)
    private List<String> attendeeEmails = new ArrayList<>();

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    @Column(nullable = false)
    private boolean cancelled = false;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    public Booking() {
    }

    public Booking(Room room, String title, String organiserEmail, List<String> attendeeEmails,
                   LocalDateTime startTime, LocalDateTime endTime, LocalDateTime createdTime) {
        this(room, title, organiserEmail, attendeeEmails, startTime, endTime, createdTime, null);
    }

    public Booking(Room room, String title, String organiserEmail, List<String> attendeeEmails,
                   LocalDateTime startTime, LocalDateTime endTime, LocalDateTime createdTime, String idempotencyKey) {
        this.room = room;
        this.title = title;
        this.organiserEmail = organiserEmail;
        this.attendeeEmails = attendeeEmails != null ? attendeeEmails : new ArrayList<>();
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdTime = createdTime;
        this.cancelled = false;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
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

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
