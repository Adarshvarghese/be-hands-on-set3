package com.example.meetingroom.service;

import com.example.meetingroom.dto.BookingResponse;
import com.example.meetingroom.dto.CreateBookingRequest;
import com.example.meetingroom.dto.FreeSlotResponse;
import com.example.meetingroom.dto.RoomResponse;
import com.example.meetingroom.entity.Booking;
import com.example.meetingroom.entity.Room;
import com.example.meetingroom.exception.BookingConflictException;
import com.example.meetingroom.exception.InvalidBookingException;
import com.example.meetingroom.exception.ResourceNotFoundException;
import com.example.meetingroom.repository.BookingRepository;
import com.example.meetingroom.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository,
                          RoomRepository roomRepository,
                          RoomService roomService,
                          Clock clock) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.roomService = roomService;
        this.clock = clock;
    }

    /**
     * Creates a booking with database-level pessimistic locking and idempotent safe-retry support.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse createBooking(CreateBookingRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        // 1. Initial basic validation
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            fieldErrors.put("title", "Title is required");
        }

        String organiserEmail = null;
        if (request.getOrganiserEmail() == null || request.getOrganiserEmail().trim().isEmpty()) {
            fieldErrors.put("organiserEmail", "Organiser email is required");
        } else {
            organiserEmail = request.getOrganiserEmail().trim();
            if (!isValidEmail(organiserEmail)) {
                fieldErrors.put("organiserEmail", "Organiser email must be a valid email address");
            }
        }

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

            if (startTime.isBefore(now)) {
                fieldErrors.put("startTime", "Booking start time cannot be in the past");
            }

            if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
                fieldErrors.put("endTime", "Booking must be on a single day");
            }

            if (!startTime.isBefore(endTime)) {
                fieldErrors.put("startTime", "Start time must be strictly before end time");
            }

            if (startTime.getMinute() % 15 != 0 || startTime.getSecond() != 0 || startTime.getNano() != 0) {
                fieldErrors.put("startTime", "Start time must use 15-minute steps (:00, :15, :30, :45)");
            }
            if (endTime.getMinute() % 15 != 0 || endTime.getSecond() != 0 || endTime.getNano() != 0) {
                fieldErrors.put("endTime", "End time must use 15-minute steps (:00, :15, :30, :45)");
            }

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

            if (startTime.isBefore(endTime)) {
                Duration duration = Duration.between(startTime, endTime);
                if (duration.toMinutes() > 240) {
                    fieldErrors.put("duration", "Booking duration cannot exceed 4 hours");
                }
            }
        }

        if (request.getRoomId() == null && (request.getRoomName() == null || request.getRoomName().trim().isEmpty())) {
            fieldErrors.put("roomId", "Room identifier (roomId or roomName) must be provided");
        }

        // If field validation fails, immediately return 400
        if (!fieldErrors.isEmpty()) {
            throw new InvalidBookingException("Validation failed for one or more fields", fieldErrors);
        }

        // 2. Resolve and lock room with PESSIMISTIC_WRITE lock (SELECT FOR UPDATE)
        // This ensures strict concurrency safety across multiple simultaneous requests or instances!
        Room room;
        if (request.getRoomId() != null) {
            room = roomRepository.findByIdWithLock(request.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + request.getRoomId()));
        } else {
            room = roomRepository.findByNameWithLock(request.getRoomName().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Room not found with name: " + request.getRoomName()));
        }

        if (!room.isActive()) {
            throw new InvalidBookingException("room", "Room '" + room.getName() + "' is not active and cannot be booked");
        }

        if (attendeeSet.size() > room.getCapacity()) {
            throw new InvalidBookingException("attendeeEmails", String.format(
                    "Number of attendees (%d) exceeds room capacity (%d)",
                    attendeeSet.size(), room.getCapacity()));
        }

        // 3. Safe Retries / Idempotency Check
        // A) If explicit idempotency key is provided, check if already exists
        String idempotencyKey = request.getIdempotencyKey() != null ? request.getIdempotencyKey().trim() : null;
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            Optional<Booking> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return BookingResponse.fromEntity(existing.get());
            }
        }

        // B) Automatic retry recognition: exact same room, time slot, and organiser
        List<Booking> exactMatches = bookingRepository
                .findByRoomIdAndStartTimeAndEndTimeAndOrganiserEmailIgnoreCaseAndCancelledFalse(
                        room.getId(), startTime, endTime, organiserEmail);
        if (!exactMatches.isEmpty()) {
            return BookingResponse.fromEntity(exactMatches.get(0));
        }

        // 4. Overlap Conflict Check
        List<Booking> clashes = bookingRepository.findOverlappingBookings(room.getId(), startTime, endTime);
        if (!clashes.isEmpty()) {
            Booking clashing = clashes.get(0);
            throw new BookingConflictException(clashing);
        }

        // 5. Persist booking
        Booking booking = new Booking(
                room,
                request.getTitle().trim(),
                organiserEmail,
                new ArrayList<>(attendeeSet),
                startTime,
                endTime,
                LocalDateTime.now(clock),
                idempotencyKey
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

    /**
     * Q2: Finds common free slots for given attendees on a date, each at least minDurationMinutes long,
     * within 09:00-18:00 in time order. For each slot, lists active rooms free for the whole slot
     * with sufficient capacity.
     */
    @Transactional(readOnly = true)
    public List<FreeSlotResponse> findCommonFreeSlots(LocalDate date, int minDurationMinutes, List<String> attendeeEmails) {
        if (date == null) {
            throw new InvalidBookingException("date", "Date is required");
        }
        if (minDurationMinutes <= 0) {
            throw new InvalidBookingException("minDurationMinutes", "Minimum duration must be greater than 0");
        }
        if (attendeeEmails == null || attendeeEmails.isEmpty()) {
            throw new InvalidBookingException("attendeeEmails", "At least one attendee email must be specified");
        }

        List<String> cleanedAttendees = attendeeEmails.stream()
                .filter(e -> e != null && !e.trim().isEmpty())
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());

        if (cleanedAttendees.isEmpty()) {
            throw new InvalidBookingException("attendeeEmails", "Attendee emails list cannot be empty");
        }

        LocalDateTime dayStart = LocalDateTime.of(date, WORK_START);
        LocalDateTime dayEnd = LocalDateTime.of(date, WORK_END);

        // 1. Fetch all active bookings involving any of the attendees on this date across all rooms
        List<Booking> attendeeBookings = bookingRepository.findActiveBookingsForAttendeesOnDate(
                cleanedAttendees, date.atStartOfDay(), date.plusDays(1).atStartOfDay());

        // 2. Extract and clamp busy intervals to working hours [09:00, 18:00)
        List<TimeInterval> rawBusy = new ArrayList<>();
        for (Booking b : attendeeBookings) {
            LocalDateTime start = b.getStartTime();
            LocalDateTime end = b.getEndTime();

            LocalDateTime clampedStart = start.isBefore(dayStart) ? dayStart : start;
            LocalDateTime clampedEnd = end.isAfter(dayEnd) ? dayEnd : end;

            if (clampedStart.isBefore(clampedEnd)) {
                rawBusy.add(new TimeInterval(clampedStart, clampedEnd));
            }
        }

        // 3. Sort and merge overlapping / contiguous busy intervals
        rawBusy.sort(Comparator.comparing(i -> i.start));
        List<TimeInterval> mergedBusy = new ArrayList<>();
        for (TimeInterval interval : rawBusy) {
            if (mergedBusy.isEmpty()) {
                mergedBusy.add(new TimeInterval(interval.start, interval.end));
            } else {
                TimeInterval last = mergedBusy.get(mergedBusy.size() - 1);
                if (!interval.start.isAfter(last.end)) {
                    if (interval.end.isAfter(last.end)) {
                        last.end = interval.end;
                    }
                } else {
                    mergedBusy.add(new TimeInterval(interval.start, interval.end));
                }
            }
        }

        // 4. Calculate free slots in gaps between merged busy intervals
        List<FreeSlotResponse> freeSlots = new ArrayList<>();
        LocalDateTime cursor = dayStart;

        for (TimeInterval busy : mergedBusy) {
            if (busy.start.isAfter(cursor)) {
                long gapMinutes = Duration.between(cursor, busy.start).toMinutes();
                if (gapMinutes >= minDurationMinutes) {
                    freeSlots.add(new FreeSlotResponse(cursor, busy.start, gapMinutes, new ArrayList<>()));
                }
            }
            if (busy.end.isAfter(cursor)) {
                cursor = busy.end;
            }
        }

        if (dayEnd.isAfter(cursor)) {
            long gapMinutes = Duration.between(cursor, dayEnd).toMinutes();
            if (gapMinutes >= minDurationMinutes) {
                freeSlots.add(new FreeSlotResponse(cursor, dayEnd, gapMinutes, new ArrayList<>()));
            }
        }

        // 5. "Where to meet": For each free slot, list active rooms big enough and free for whole slot
        List<Room> allRooms = roomRepository.findAll();
        int groupSize = cleanedAttendees.size();

        for (FreeSlotResponse slot : freeSlots) {
            List<RoomResponse> availableRooms = new ArrayList<>();
            for (Room room : allRooms) {
                if (room.isActive() && room.getCapacity() >= groupSize) {
                    List<Booking> roomClashes = bookingRepository.findOverlappingBookings(
                            room.getId(), slot.getStartTime(), slot.getEndTime());
                    if (roomClashes.isEmpty()) {
                        availableRooms.add(RoomResponse.fromEntity(room));
                    }
                }
            }
            slot.setAvailableRooms(availableRooms);
        }

        return freeSlots;
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private static class TimeInterval {
        LocalDateTime start;
        LocalDateTime end;

        TimeInterval(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }
}
