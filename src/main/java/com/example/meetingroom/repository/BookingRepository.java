package com.example.meetingroom.repository;

import com.example.meetingroom.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Finds active (non-cancelled) bookings in the specified room that overlap with [newStart, newEnd).
     * Back-to-back bookings (newStart == existingEnd or newEnd == existingStart) do not overlap.
     */
    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId AND b.cancelled = false " +
           "AND b.startTime < :newEnd AND b.endTime > :newStart ORDER BY b.startTime ASC")
    List<Booking> findOverlappingBookings(@Param("roomId") Long roomId,
                                         @Param("newStart") LocalDateTime newStart,
                                         @Param("newEnd") LocalDateTime newEnd);

    /**
     * Lists active (non-cancelled) bookings for a room on a given day, ordered by start time.
     */
    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId AND b.cancelled = false " +
           "AND b.startTime >= :dayStart AND b.startTime < :dayEnd " +
           "ORDER BY b.startTime ASC")
    List<Booking> findActiveByRoomIdAndDate(@Param("roomId") Long roomId,
                                           @Param("dayStart") LocalDateTime dayStart,
                                           @Param("dayEnd") LocalDateTime dayEnd);

    /**
     * Lists all bookings (including cancelled ones) for a room on a given day, ordered by start time.
     */
    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId " +
           "AND b.startTime >= :dayStart AND b.startTime < :dayEnd " +
           "ORDER BY b.startTime ASC")
    List<Booking> findAllByRoomIdAndDate(@Param("roomId") Long roomId,
                                        @Param("dayStart") LocalDateTime dayStart,
                                        @Param("dayEnd") LocalDateTime dayEnd);
}
