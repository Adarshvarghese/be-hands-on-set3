package com.example.meetingroom;

import com.example.meetingroom.dto.CreateBookingRequest;
import com.example.meetingroom.entity.Room;
import com.example.meetingroom.repository.BookingRepository;
import com.example.meetingroom.repository.RoomRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class MeetingRoomIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Room boardRoom;
    private Room quietPod;
    private Room inactiveRoom;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        boardRoom = roomRepository.findByNameIgnoreCase("Boardroom").orElseThrow();
        quietPod = roomRepository.findByNameIgnoreCase("Quiet Pod").orElseThrow();
        inactiveRoom = roomRepository.findByNameIgnoreCase("Maintenance Suite").orElseThrow();
    }

    @Test
    @DisplayName("Startup seeds at least 5 rooms with unique names, capacity, and active flag")
    void testStartupSeeding() {
        List<Room> rooms = roomRepository.findAll();
        assertTrue(rooms.size() >= 5, "Must seed at least 5 rooms on startup");
        assertTrue(rooms.stream().anyMatch(r -> r.getName().equals("Boardroom") && r.getCapacity() == 12 && r.isActive()));
        assertTrue(rooms.stream().anyMatch(r -> r.getName().equals("Quiet Pod") && r.getCapacity() == 2 && r.isActive()));
        assertTrue(rooms.stream().anyMatch(r -> r.getName().equals("Maintenance Suite") && !r.isActive()));
    }

    @Test
    @DisplayName("Acceptance Criteria 1 & 2: Overlap 10:00-11:00 vs 10:30-11:30 returns 409, back-to-back 11:00-12:00 returns 201")
    void testAcceptanceCriteria1And2() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(2);

        // 1. Booking 10:00 - 11:00 -> 201 Created
        CreateBookingRequest req1 = new CreateBookingRequest(
                boardRoom.getId(),
                "Quarterly Planning",
                "organiser@example.com",
                List.of("organiser@example.com", "dev1@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(10, 0)),
                LocalDateTime.of(testDate, LocalTime.of(11, 0))
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Quarterly Planning"))
                .andExpect(jsonPath("$.roomName").value("Boardroom"))
                .andExpect(jsonPath("$.cancelled").value(false));

        // 2. Booking 10:30 - 11:30 -> 409 Conflict with clashing booking named
        CreateBookingRequest req2 = new CreateBookingRequest(
                boardRoom.getId(),
                "Design Sync",
                "designer@example.com",
                List.of("designer@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(10, 30)),
                LocalDateTime.of(testDate, LocalTime.of(11, 30))
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("Quarterly Planning")))
                .andExpect(jsonPath("$.clashingBooking.title").value("Quarterly Planning"))
                .andExpect(jsonPath("$.clashingBooking.startTime").value(containsString("10:00:00")))
                .andExpect(jsonPath("$.clashingBooking.endTime").value(containsString("11:00:00")));

        // 3. Booking 11:00 - 12:00 afterwards returns 201 (back-to-back allowed)
        CreateBookingRequest req3 = new CreateBookingRequest(
                boardRoom.getId(),
                "Back to Back Sync",
                "manager@example.com",
                List.of("manager@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(11, 0)),
                LocalDateTime.of(testDate, LocalTime.of(12, 0))
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Back to Back Sync"));
    }

    @Test
    @DisplayName("Acceptance Criteria 3: Booking completely covering an existing one (09:00-12:00 over 10:00-10:30) is rejected with 409")
    void testAcceptanceCriteria3CompleteCoverOverlap() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(3);

        // First booking: 10:00 - 10:30
        CreateBookingRequest req1 = new CreateBookingRequest(
                boardRoom.getId(),
                "Quick Standup",
                "lead@example.com",
                List.of("lead@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(10, 0)),
                LocalDateTime.of(testDate, LocalTime.of(10, 30))
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Second booking completely covering the first: 09:00 - 12:00
        CreateBookingRequest req2 = new CreateBookingRequest(
                boardRoom.getId(),
                "All Hands Workshop",
                "director@example.com",
                List.of("director@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(9, 0)),
                LocalDateTime.of(testDate, LocalTime.of(12, 0))
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("Quick Standup")))
                .andExpect(jsonPath("$.clashingBooking.title").value("Quick Standup"));
    }

    @Test
    @DisplayName("Cancelled booking no longer blocks the room and can be re-booked")
    void testCancelledBookingUnblocksRoom() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(4);

        // 1. Create booking 14:00 - 15:00
        CreateBookingRequest req = new CreateBookingRequest(
                boardRoom.getId(),
                "Tentative Meeting",
                "organiser@example.com",
                List.of("organiser@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(14, 0)),
                LocalDateTime.of(testDate, LocalTime.of(15, 0))
        );

        String responseStr = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long bookingId = objectMapper.readTree(responseStr).get("id").asLong();

        // 2. Overlap attempt before cancelling -> 409 Conflict
        CreateBookingRequest clashingReq = new CreateBookingRequest(
                boardRoom.getId(),
                "Another Meeting",
                "other@example.com",
                List.of("other@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(14, 0)),
                LocalDateTime.of(testDate, LocalTime.of(15, 0))
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clashingReq)))
                .andExpect(status().isConflict());

        // 3. Cancel the booking
        mockMvc.perform(delete("/api/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.cancelled").value(true));

        // 4. Verify get one booking returns cancelled: true
        mockMvc.perform(get("/api/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true));

        // 5. Booking the same time slot afterwards succeeds (201) because cancelled booking no longer blocks the room!
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clashingReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Another Meeting"))
                .andExpect(jsonPath("$.cancelled").value(false));
    }

    @Test
    @DisplayName("List a room's bookings for a date in time order")
    void testListRoomBookingsInTimeOrder() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(5);

        // Book 15:00 - 16:00
        CreateBookingRequest b3 = new CreateBookingRequest(
                boardRoom.getId(), "Afternoon Sync", "org@example.com", List.of("org@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(15, 0)),
                LocalDateTime.of(testDate, LocalTime.of(16, 0))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(b3))).andExpect(status().isCreated());

        // Book 09:00 - 10:00
        CreateBookingRequest b1 = new CreateBookingRequest(
                boardRoom.getId(), "Morning Kickoff", "org@example.com", List.of("org@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(9, 0)),
                LocalDateTime.of(testDate, LocalTime.of(10, 0))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(b1))).andExpect(status().isCreated());

        // Book 12:00 - 13:00
        CreateBookingRequest b2 = new CreateBookingRequest(
                boardRoom.getId(), "Lunch Workshop", "org@example.com", List.of("org@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(12, 0)),
                LocalDateTime.of(testDate, LocalTime.of(13, 0))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(b2))).andExpect(status().isCreated());

        // Query list for that date: must be in time order (b1, b2, b3)
        mockMvc.perform(get("/api/rooms/" + boardRoom.getId() + "/bookings")
                        .param("date", testDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].title").value("Morning Kickoff"))
                .andExpect(jsonPath("$[0].startTime").value(containsString("09:00:00")))
                .andExpect(jsonPath("$[1].title").value("Lunch Workshop"))
                .andExpect(jsonPath("$[1].startTime").value(containsString("12:00:00")))
                .andExpect(jsonPath("$[2].title").value("Afternoon Sync"))
                .andExpect(jsonPath("$[2].startTime").value(containsString("15:00:00")));
    }

    @Test
    @DisplayName("Number of attendees cannot exceed room capacity (and organiser is always attendee)")
    void testCapacityAndOrganiserAttendance() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(6);
        // Quiet Pod has capacity 2

        // Organiser + 1 attendee = 2 (Allowed)
        CreateBookingRequest validReq = new CreateBookingRequest(
                quietPod.getId(),
                "1-on-1 Catchup",
                "manager@example.com",
                List.of("employee@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(10, 0)),
                LocalDateTime.of(testDate, LocalTime.of(10, 30))
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendeeEmails", hasSize(2)))
                .andExpect(jsonPath("$.attendeeEmails", containsInAnyOrder("manager@example.com", "employee@example.com")));

        // Organiser + 2 attendees = 3 (Exceeds capacity 2 -> 400 Bad Request with field error)
        CreateBookingRequest invalidReq = new CreateBookingRequest(
                quietPod.getId(),
                "Trio Meeting",
                "manager@example.com",
                List.of("employee1@example.com", "employee2@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(11, 0)),
                LocalDateTime.of(testDate, LocalTime.of(11, 30))
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.attendeeEmails").value(containsString("exceeds room capacity")));
    }

    @Test
    @DisplayName("Invalid inputs return 400 with a message per invalid field")
    void testInvalidInputs() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(7);

        // Multiple validation errors in one request:
        // 1. Outside working hours (starts at 08:30)
        // 2. Not 15-minute steps (ends at 13:07)
        // 3. More than 4 hours duration (08:30 to 13:07 = 4h37m)
        // 4. Missing title
        // 5. Invalid email format
        CreateBookingRequest req = new CreateBookingRequest();
        req.setRoomId(boardRoom.getId());
        req.setTitle("");
        req.setOrganiserEmail("not-an-email");
        req.setStartTime(LocalDateTime.of(testDate, LocalTime.of(8, 30)));
        req.setEndTime(LocalDateTime.of(testDate, LocalTime.of(13, 7)));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.organiserEmail").exists())
                .andExpect(jsonPath("$.fieldErrors.startTime").exists())
                .andExpect(jsonPath("$.fieldErrors.endTime").exists())
                .andExpect(jsonPath("$.fieldErrors.duration").exists());
    }

    @Test
    @DisplayName("Booking across multiple days returns 400 Bad Request")
    void testMultipleDaysRejected() throws Exception {
        LocalDate today = LocalDate.now().plusDays(8);
        CreateBookingRequest req = new CreateBookingRequest(
                boardRoom.getId(),
                "Overnight Hackathon",
                "org@example.com",
                List.of("org@example.com"),
                LocalDateTime.of(today, LocalTime.of(14, 0)),
                LocalDateTime.of(today.plusDays(1), LocalTime.of(15, 0))
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endTime").value(containsString("single day")));
    }

    @Test
    @DisplayName("Booking in the past returns 400 Bad Request")
    void testBookingInPastRejected() throws Exception {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        CreateBookingRequest req = new CreateBookingRequest(
                boardRoom.getId(),
                "Past Retrospective",
                "org@example.com",
                List.of("org@example.com"),
                LocalDateTime.of(yesterday, LocalTime.of(10, 0)),
                LocalDateTime.of(yesterday, LocalTime.of(11, 0))
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startTime").value(containsString("cannot be in the past")));
    }

    @Test
    @DisplayName("Booking an inactive room returns 400 Bad Request")
    void testBookingInactiveRoomRejected() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(9);
        CreateBookingRequest req = new CreateBookingRequest(
                inactiveRoom.getId(),
                "Secret Meeting",
                "org@example.com",
                List.of("org@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(10, 0)),
                LocalDateTime.of(testDate, LocalTime.of(11, 0))
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.room").value(containsString("not active")));
    }

    @Test
    @DisplayName("Unknown room or unknown booking returns 404 Not Found")
    void testUnknownRoomAndBookingReturn404() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(10);

        // Unknown Room in create booking
        CreateBookingRequest req = new CreateBookingRequest(
                99999L,
                "Unknown Room Meeting",
                "org@example.com",
                List.of("org@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(10, 0)),
                LocalDateTime.of(testDate, LocalTime.of(11, 0))
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("99999")));

        // Unknown Booking in GET
        mockMvc.perform(get("/api/bookings/88888"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("88888")));

        // Unknown Booking in Cancel
        mockMvc.perform(delete("/api/bookings/77777"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("77777")));
    }

    @Test
    @DisplayName("Support roomName lookup in create booking request")
    void testCreateBookingUsingRoomName() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(11);
        CreateBookingRequest req = new CreateBookingRequest();
        req.setRoomName("Apollo");
        req.setTitle("Apollo Moonshot Review");
        req.setOrganiserEmail("director@nasa.gov");
        req.setStartTime(LocalDateTime.of(testDate, LocalTime.of(14, 0)));
        req.setEndTime(LocalDateTime.of(testDate, LocalTime.of(15, 0)));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomName").value("Apollo"))
                .andExpect(jsonPath("$.title").value("Apollo Moonshot Review"));
    }

    @Test
    @DisplayName("Working hours boundary: Exactly 09:00 to 18:00 allowed, outside rejected")
    void testWorkingHoursBoundaries() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(12);

        // Boundary test: Starts exactly at 09:00, ends at 13:00 (4 hrs) -> Allowed
        CreateBookingRequest morning = new CreateBookingRequest(
                boardRoom.getId(), "Morning Shift", "lead@company.com", List.of("lead@company.com"),
                LocalDateTime.of(testDate, LocalTime.of(9, 0)),
                LocalDateTime.of(testDate, LocalTime.of(13, 0))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(morning))).andExpect(status().isCreated());

        // Boundary test: Starts at 14:00, ends exactly at 18:00 (4 hrs) -> Allowed
        CreateBookingRequest afternoon = new CreateBookingRequest(
                boardRoom.getId(), "Evening Shift", "lead@company.com", List.of("lead@company.com"),
                LocalDateTime.of(testDate, LocalTime.of(14, 0)),
                LocalDateTime.of(testDate, LocalTime.of(18, 0))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(afternoon))).andExpect(status().isCreated());

        // Starting before 09:00 (e.g. 08:45) -> Rejected (400)
        CreateBookingRequest tooEarly = new CreateBookingRequest(
                boardRoom.getId(), "Early Shift", "lead@company.com", List.of("lead@company.com"),
                LocalDateTime.of(testDate.plusDays(1), LocalTime.of(8, 45)),
                LocalDateTime.of(testDate.plusDays(1), LocalTime.of(9, 45))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tooEarly)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startTime").value(containsString("within working hours")));

        // Ending after 18:00 (e.g. 18:15) -> Rejected (400)
        CreateBookingRequest tooLate = new CreateBookingRequest(
                boardRoom.getId(), "Late Shift", "lead@company.com", List.of("lead@company.com"),
                LocalDateTime.of(testDate.plusDays(1), LocalTime.of(17, 15)),
                LocalDateTime.of(testDate.plusDays(1), LocalTime.of(18, 15))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tooLate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endTime").value(containsString("within working hours")));
    }

    @Test
    @DisplayName("Duration rule: At most 4 hours allowed, 4h15m rejected")
    void testDurationLimit() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(14);

        // 4 hours 15 minutes (09:00 to 13:15) -> Rejected (400)
        CreateBookingRequest tooLong = new CreateBookingRequest(
                boardRoom.getId(), "Marathon", "lead@company.com", List.of("lead@company.com"),
                LocalDateTime.of(testDate, LocalTime.of(9, 0)),
                LocalDateTime.of(testDate, LocalTime.of(13, 15))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.duration").value(containsString("cannot exceed 4 hours")));
    }

    @Test
    @DisplayName("Start before end: Equal start and end or start after end returns 400 Bad Request")
    void testStartBeforeEnd() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(15);

        // Start == End (10:00 to 10:00)
        CreateBookingRequest zeroDuration = new CreateBookingRequest(
                boardRoom.getId(), "Instant Meeting", "lead@company.com", List.of("lead@company.com"),
                LocalDateTime.of(testDate, LocalTime.of(10, 0)),
                LocalDateTime.of(testDate, LocalTime.of(10, 0))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(zeroDuration)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startTime").value(containsString("strictly before end time")));

        // Start > End (11:00 to 10:00)
        CreateBookingRequest backwards = new CreateBookingRequest(
                boardRoom.getId(), "Time Travel Meeting", "lead@company.com", List.of("lead@company.com"),
                LocalDateTime.of(testDate, LocalTime.of(11, 0)),
                LocalDateTime.of(testDate, LocalTime.of(10, 0))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(backwards)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startTime").value(containsString("strictly before end time")));
    }

    @Test
    @DisplayName("Different rooms can be booked at the same time without conflict")
    void testDifferentRoomsDoNotConflict() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(16);

        // Book Boardroom 10:00 to 11:00
        CreateBookingRequest req1 = new CreateBookingRequest(
                boardRoom.getId(), "Boardroom Sync", "lead@company.com", List.of("lead@company.com"),
                LocalDateTime.of(testDate, LocalTime.of(10, 0)),
                LocalDateTime.of(testDate, LocalTime.of(11, 0))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1))).andExpect(status().isCreated());

        // Book Quiet Pod at same time (10:00 to 11:00) -> 201 Created!
        CreateBookingRequest req2 = new CreateBookingRequest(
                quietPod.getId(), "Pod Sync", "lead@company.com", List.of("lead@company.com"),
                LocalDateTime.of(testDate, LocalTime.of(10, 0)),
                LocalDateTime.of(testDate, LocalTime.of(11, 0))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Alternative cancel endpoints (POST /cancel and PATCH /cancel) also cancel booking")
    void testAlternativeCancelEndpoints() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(17);

        // Create booking 1
        CreateBookingRequest req1 = new CreateBookingRequest(
                boardRoom.getId(), "Booking For POST Cancel", "lead@company.com", List.of("lead@company.com"),
                LocalDateTime.of(testDate, LocalTime.of(10, 0)),
                LocalDateTime.of(testDate, LocalTime.of(11, 0))
        );
        String res1 = mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id1 = objectMapper.readTree(res1).get("id").asLong();

        // Cancel via POST /api/bookings/{id}/cancel
        mockMvc.perform(post("/api/bookings/" + id1 + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true));

        // Create booking 2
        CreateBookingRequest req2 = new CreateBookingRequest(
                boardRoom.getId(), "Booking For PATCH Cancel", "lead@company.com", List.of("lead@company.com"),
                LocalDateTime.of(testDate, LocalTime.of(11, 0)),
                LocalDateTime.of(testDate, LocalTime.of(12, 0))
        );
        String res2 = mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id2 = objectMapper.readTree(res2).get("id").asLong();

        // Cancel via PATCH /api/bookings/{id}/cancel
        mockMvc.perform(patch("/api/bookings/" + id2 + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true));
    }
}

