package com.example.meetingroom;

import com.example.meetingroom.dto.CreateBookingRequest;
import com.example.meetingroom.dto.FreeSlotsRequest;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class FreeSlotsAndConcurrencyTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Room boardRoom;
    private Room apollo;
    private Room orion;
    private Room galaxy;
    private Room quietPod;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        boardRoom = roomRepository.findByNameIgnoreCase("Boardroom").orElseThrow();
        apollo = roomRepository.findByNameIgnoreCase("Apollo").orElseThrow();
        orion = roomRepository.findByNameIgnoreCase("Orion").orElseThrow();
        galaxy = roomRepository.findByNameIgnoreCase("Galaxy").orElseThrow();
        quietPod = roomRepository.findByNameIgnoreCase("Quiet Pod").orElseThrow();
    }

    @Test
    @DisplayName("Q2 Acceptance Criteria 1: Common free slots for Attendees A and B with D=30")
    void testCommonFreeSlotsForAttendeesAAndB() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(20);

        String attendeeA = "attendeeA@company.com";
        String attendeeB = "attendeeB@company.com";

        // Attendee A has booking 10:00–11:00 (in Boardroom)
        CreateBookingRequest b1 = new CreateBookingRequest(
                boardRoom.getId(), "A Meeting 1", attendeeA, List.of(attendeeA),
                LocalDateTime.of(testDate, LocalTime.of(10, 0)),
                LocalDateTime.of(testDate, LocalTime.of(11, 0))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(b1))).andExpect(status().isCreated());

        // Attendee A has booking 09:30–10:30 (in Apollo, overlapping their own meeting)
        CreateBookingRequest b2 = new CreateBookingRequest(
                apollo.getId(), "A Meeting 2", attendeeA, List.of(attendeeA),
                LocalDateTime.of(testDate, LocalTime.of(9, 30)),
                LocalDateTime.of(testDate, LocalTime.of(10, 30))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(b2))).andExpect(status().isCreated());

        // Attendee B has booking 13:00–14:00 (in Orion)
        CreateBookingRequest b3 = new CreateBookingRequest(
                orion.getId(), "B Meeting 1", attendeeB, List.of(attendeeB),
                LocalDateTime.of(testDate, LocalTime.of(13, 0)),
                LocalDateTime.of(testDate, LocalTime.of(14, 0))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(b3))).andExpect(status().isCreated());

        // Attendee B has booking 13:15–13:45 (in Galaxy, inside their other meeting)
        CreateBookingRequest b4 = new CreateBookingRequest(
                galaxy.getId(), "B Meeting 2", attendeeB, List.of(attendeeB),
                LocalDateTime.of(testDate, LocalTime.of(13, 15)),
                LocalDateTime.of(testDate, LocalTime.of(13, 45))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(b4))).andExpect(status().isCreated());

        // Query common free slots for A and B with D = 30
        FreeSlotsRequest req = new FreeSlotsRequest(testDate, 30, List.of(attendeeA, attendeeB));

        mockMvc.perform(post("/api/free-slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                // Slot 1: 09:00 - 09:30
                .andExpect(jsonPath("$[0].startTime").value(containsString("09:00:00")))
                .andExpect(jsonPath("$[0].endTime").value(containsString("09:30:00")))
                .andExpect(jsonPath("$[0].durationMinutes").value(30))
                // Slot 2: 11:00 - 13:00
                .andExpect(jsonPath("$[1].startTime").value(containsString("11:00:00")))
                .andExpect(jsonPath("$[1].endTime").value(containsString("13:00:00")))
                .andExpect(jsonPath("$[1].durationMinutes").value(120))
                // Slot 3: 14:00 - 18:00
                .andExpect(jsonPath("$[2].startTime").value(containsString("14:00:00")))
                .andExpect(jsonPath("$[2].endTime").value(containsString("18:00:00")))
                .andExpect(jsonPath("$[2].durationMinutes").value(240));
    }

    @Test
    @DisplayName("Q2 Where to meet: Lists active rooms free for whole slot and big enough for group")
    void testWhereToMeetRoomFiltering() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(21);
        String attendee = "user@example.com";

        // Quiet Pod has capacity 2.
        // Boardroom has capacity 12.
        // Let's create a booking in Boardroom between 09:15 and 09:45
        CreateBookingRequest b = new CreateBookingRequest(
                boardRoom.getId(), "Mid-slot Meeting", "other@example.com", List.of("other@example.com"),
                LocalDateTime.of(testDate, LocalTime.of(9, 15)),
                LocalDateTime.of(testDate, LocalTime.of(9, 45))
        );
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(b))).andExpect(status().isCreated());

        // For a group of 3 attendees with no busy meetings: Free slot is 09:00–18:00
        // - Boardroom is booked 09:15-09:45, so it is NOT free for whole slot!
        // - Quiet Pod only has capacity 2, but group size is 3, so it is NOT big enough!
        // - Inactive Maintenance Suite is NOT active!
        // - Apollo (capacity 6), Orion (capacity 4), Galaxy (capacity 20) are free and big enough!
        FreeSlotsRequest req = new FreeSlotsRequest(testDate, 60, List.of("u1@x.com", "u2@x.com", "u3@x.com"));

        mockMvc.perform(post("/api/free-slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].availableRooms[*].name", hasItems("Apollo", "Orion", "Galaxy")))
                .andExpect(jsonPath("$[0].availableRooms[*].name", not(hasItem("Boardroom"))))
                .andExpect(jsonPath("$[0].availableRooms[*].name", not(hasItem("Quiet Pod"))))
                .andExpect(jsonPath("$[0].availableRooms[*].name", not(hasItem("Maintenance Suite"))));
    }

    @Test
    @DisplayName("Q2 Acceptance Criteria 2: 20 simultaneous requests under load: Exactly 1 succeeds, 19 get 409, 1 in DB")
    void testSimultaneousRequestsUnderLoad() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(22);
        int numThreads = 20;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch readyLatch = new CountDownLatch(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // wait for simultaneous trigger
                    CreateBookingRequest req = new CreateBookingRequest(
                            boardRoom.getId(),
                            "Concurrent Meeting " + index,
                            "user" + index + "@company.com",
                            List.of("user" + index + "@company.com"),
                            LocalDateTime.of(testDate, LocalTime.of(10, 0)),
                            LocalDateTime.of(testDate, LocalTime.of(11, 0)),
                            "key-" + index // unique idempotency key per competitor
                    );

                    MvcResult result = mockMvc.perform(post("/api/bookings")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    statuses.add(status);
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // fire all 20 threads simultaneously!
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        assertEquals(1, successCount.get(), "Exactly 1 request must succeed with 201");
        assertEquals(19, conflictCount.get(), "Exactly 19 requests must get 409 Conflict");
        assertEquals(1, bookingRepository.count(), "Database must hold exactly 1 booking");
    }

    @Test
    @DisplayName("Q2 Acceptance Criteria 3: Retrying the same booking request creates only one booking")
    void testSafeRetriesIdempotency() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(23);

        CreateBookingRequest req = new CreateBookingRequest(
                boardRoom.getId(),
                "Budget Meeting",
                "cfo@company.com",
                List.of("cfo@company.com"),
                LocalDateTime.of(testDate, LocalTime.of(11, 0)),
                LocalDateTime.of(testDate, LocalTime.of(12, 0))
        );

        String idempotencyKey = UUID.randomUUID().toString();

        // 1. First attempt with Idempotency-Key header
        MvcResult firstAttempt = mockMvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long firstId = objectMapper.readTree(firstAttempt.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(1, bookingRepository.count());

        // 2. Retry with the same Idempotency-Key header
        MvcResult retryAttempt = mockMvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        Long retryId = objectMapper.readTree(retryAttempt.getResponse().getContentAsString()).get("id").asLong();

        // Must return the exact same booking, not create a second one!
        assertEquals(firstId, retryId);
        assertEquals(1, bookingRepository.count(), "Retrying must not create a second booking");

        // 3. Automatic safe retry without header (resending exact same request body from same organizer)
        MvcResult autoRetry = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        Long autoRetryId = objectMapper.readTree(autoRetry.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(firstId, autoRetryId);
        assertEquals(1, bookingRepository.count(), "Automatic retry must return existing booking and not duplicate");
    }

    @Test
    @DisplayName("Cancelled bookings do not block attendees in free slots")
    void testCancelledBookingsIgnoredInFreeSlots() throws Exception {
        LocalDate testDate = LocalDate.now().plusDays(24);
        String attendee = "dev@company.com";

        // Create booking 10:00 - 11:00
        CreateBookingRequest b = new CreateBookingRequest(
                boardRoom.getId(), "To be cancelled", attendee, List.of(attendee),
                LocalDateTime.of(testDate, LocalTime.of(10, 0)),
                LocalDateTime.of(testDate, LocalTime.of(11, 0))
        );
        String res = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(b)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long bookingId = objectMapper.readTree(res).get("id").asLong();

        // Cancel the booking
        mockMvc.perform(delete("/api/bookings/" + bookingId)).andExpect(status().isOk());

        // Query free slots: attendee should be free whole day 09:00 - 18:00
        FreeSlotsRequest req = new FreeSlotsRequest(testDate, 60, List.of(attendee));

        mockMvc.perform(post("/api/free-slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].startTime").value(containsString("09:00:00")))
                .andExpect(jsonPath("$[0].endTime").value(containsString("18:00:00")))
                .andExpect(jsonPath("$[0].durationMinutes").value(540));
    }
}
