package com.example.meetingroom.service;

import com.example.meetingroom.dto.RoomResponse;
import com.example.meetingroom.entity.Room;
import com.example.meetingroom.exception.ResourceNotFoundException;
import com.example.meetingroom.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public List<RoomResponse> getAllRooms() {
        return roomRepository.findAll().stream()
                .map(RoomResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public RoomResponse getRoomById(Long id) {
        Room room = findRoomById(id);
        return RoomResponse.fromEntity(room);
    }

    public Room findRoomById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + id));
    }

    public Room findRoomByName(String name) {
        return roomRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with name: " + name));
    }

    public Room resolveRoom(Long roomId, String roomName) {
        if (roomId != null) {
            return findRoomById(roomId);
        }
        if (roomName != null && !roomName.trim().isEmpty()) {
            return findRoomByName(roomName.trim());
        }
        return null;
    }
}
