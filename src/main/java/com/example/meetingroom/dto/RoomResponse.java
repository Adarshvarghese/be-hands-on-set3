package com.example.meetingroom.dto;

import com.example.meetingroom.entity.Room;

public class RoomResponse {

    private Long id;
    private String name;
    private int capacity;
    private boolean active;

    public RoomResponse() {
    }

    public RoomResponse(Long id, String name, int capacity, boolean active) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.active = active;
    }

    public static RoomResponse fromEntity(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getCapacity(), room.isActive());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
