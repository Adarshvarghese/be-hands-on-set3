package com.example.meetingroom.config;

import com.example.meetingroom.entity.Room;
import com.example.meetingroom.repository.RoomRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final RoomRepository roomRepository;

    public DataSeeder(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    public void run(String... args) {
        if (roomRepository.count() == 0) {
            List<Room> initialRooms = List.of(
                    new Room("Boardroom", 12, true),
                    new Room("Apollo", 6, true),
                    new Room("Orion", 4, true),
                    new Room("Galaxy", 20, true),
                    new Room("Quiet Pod", 2, true),
                    new Room("Maintenance Suite", 5, false) // seeded inactive room for validation testing
            );
            roomRepository.saveAll(initialRooms);
        }
    }
}
