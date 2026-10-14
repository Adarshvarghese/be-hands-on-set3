package com.example.meetingroom.service;

import com.example.meetingroom.dto.BookingResponse;
import com.example.meetingroom.dto.CreateBookingRequest;
import com.example.meetingroom.entity.Booking;
import com.example.meetingroom.entity.Room;
import com.example.meetingroom.exception.BookingConflictException;
import com.example.meetingroom.exception.InvalidBookingException;
import com.example.meetingroom.exception.ResourceNotFoundException;
import com.example.meetingroom.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);

    private final BookingRepository bookingRepository;
    private final RoomService roomService;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository, RoomService roomService, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.roomService = roomService;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        // 1. Resolve room
        Room room = null;
        if (request.getRoomId() == null && (request.getRoomName() == null || request.getRoomName().trim().isEmpty())) {
            fieldErrors.put("roomId", "Room identifier (roomId or roomName) must be provided");
        } else {
            // If unknown room, resolveRoom throws ResourceNotFoundException (404)
            room = roomService.resolveRoom(request.getRoomId(), request.getRoomName());
            if (!room.isActive()) {
                fieldErrors.put("room", "Room '" + room.getName() + "' is not active and cannot be booked");
            }
        }

        // 2. Validate Title
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            fieldErrors.put("title", "Title is required");
        }

        // 3. Validate Organiser Email
        String organiserEmail = null;
        if (request.getOrganiserEmail() == null || request.getOrganiserEmail().trim().isEmpty()) {
            fieldErrors.put("organiserEmail", "Organiser email is required");
        } else {
            organiserEmail = request.getOrganiserEmail().trim();
            if (!isValidEmail(organiserEmail)) {
                fieldErrors.put("organiserEmail", "Organiser email must be a valid email address");
            }
        }

        // 4. Validate Attendees & Capacity (organiser is always an attendee)
        Set<String> attendeeSet = new LinkedHashSet<>();
        if (organiserEmail != null && isValidEmail(organiserEmail)) {
            attendeeSet.add(organiserEmail.toLowerCase());
        }

        if (request.getAttendeeEmails() != null) {
            for (String email : request.getAttendeeEmails()) {
                if (email == null || email.trim().isEmpty() || !isValidEmail(email.trim())) {
                    fieldErrors.put("attendeeEmails", "Invalid email address in attendee list: " + email);
                    break;
                }
                attendeeSet.add(email.trim().toLowerCase());
            }
        }

        if (room != null && attendeeSet.size() > room.getCapacity()) {
            fieldErrors.put("attendeeEmails", String.format(
                    "Number of attendees (%d) exceeds room capacity (%d)",
                    attendeeSet.size(), room.getCapacity()));
        }

        // 5. Validate Date & Times
        LocalDateTime startTime = request.getStartTime();
        LocalDateTime endTime = request.getEndTime();

        if (startTime == null) {
            fieldErrors.put("startTime", "Start time is required");
        }
        if (endTime == null) {
            fieldErrors.put("endTime", "End time is required");
        }

        if (startTime != null && endTime != null) {
            LocalDateTime now = LocalDateTime.now(clock);

            // Cannot be in the past
            if (startTime.isBefore(now)) {
                fieldErrors.put("startTime", "Booking start time cannot be in the past");
            }

            // Must be on a single day
            if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
                fieldErrors.put("endTime", "Booking must be on a single day");
            }

            // Start before it ends
            if (!startTime.isBefore(endTime)) {
                fieldErrors.put("startTime", "Start time must be strictly before end time");
            }

            // 15-minute steps
            if (startTime.getMinute() % 15 != 0 || startTime.getSecond() != 0 || startTime.getNano() != 0) {
                fieldErrors.put("startTime", "Start time must use 15-minute steps (:00, :15, :30, :45)");
            }
            if (endTime.getMinute() % 15 != 0 || endTime.getSecond() != 0 || endTime.getNano() != 0) {
                fieldErrors.put("endTime", "End time must use 15-minute steps (:00, :15, :30, :45)");
            }

            // Within working hours 09:00 to 18:00
            LocalTime startLocal = startTime.toLocalTime();
            LocalTime endLocal = endTime.toLocalTime();

            if (startLocal.isBefore(WORK_START)) {
                fieldErrors.put("startTime", "Booking start time must be within working hours (09:00 to 18:00)");
            } else if (!startLocal.isBefore(WORK_END)) {
                fieldErrors.put("startTime", "Booking start time must be before 18:00");
            }

            if (!endLocal.isAfter(WORK_START)) {
                fieldErrors.put("endTime", "Booking end time must be after 09:00");
            } else if (endLocal.isAfter(WORK_END)) {
                fieldErrors.put("endTime", "Booking end time must be within working hours (09:00 to 18:00)");
            }

            // Last at most 4 hours (240 minutes)
            if (startTime.isBefore(endTime)) {
                Duration duration = Duration.between(startTime, endTime);
                if (duration.toMinutes() > 240) {
                    fieldErrors.put("duration", "Booking duration cannot exceed 4 hours");
                }
            }
        }

        // If any validation failed, return 400 with a message per invalid field
        if (!fieldErrors.isEmpty()) {
            throw new InvalidBookingException("Validation failed for one or more fields", fieldErrors);
        }

        // 6. Overlap Conflict Check
        // A booking can't overlap another booking in the same room.
        // Back-to-back bookings (e.g. 10:00-11:00 and 11:00-12:00) do NOT overlap.
        // Cancelled bookings no longer block the room.
        List<Booking> clashes = bookingRepository.findOverlappingBookings(room.getId(), startTime, endTime);
        if (!clashes.isEmpty()) {
            Booking clashing = clashes.get(0);
            throw new BookingConflictException(clashing);
        }

        // 7. Save and return
        Booking booking = new Booking(
                room,
                request.getTitle().trim(),
                organiserEmail,
                new ArrayList<>(attendeeSet),
                startTime,
                endTime,
                LocalDateTime.now(clock)
        );

        Booking saved = bookingRepository.save(booking);
        return BookingResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + id));
        return BookingResponse.fromEntity(booking);
    }

    @Transactional
    public BookingResponse cancelBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + id));

        booking.setCancelled(true);
        Booking saved = bookingRepository.save(booking);
        return BookingResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsForRoomAndDate(Long roomId, LocalDate date, boolean includeCancelled) {
        // Ensure room exists
        roomService.findRoomById(roomId);

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<Booking> bookings = includeCancelled
                ? bookingRepository.findAllByRoomIdAndDate(roomId, dayStart, dayEnd)
                : bookingRepository.findActiveByRoomIdAndDate(roomId, dayStart, dayEnd);

        return bookings.stream()
                .map(BookingResponse::fromEntity)
                .collect(Collectors.toList());
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
