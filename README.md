# be-hands-on-set3

Meeting room booking backend service built with Java 21, Spring Boot 3.4.3, Spring Data JPA, and H2 database.

## Problem
Provides a REST API for meeting room booking with capacity enforcement, business rule validation (working hours 09:00–18:00, 15-minute steps, single-day, max 4 hours), and non-overlapping scheduling where cancelled bookings release room availability.

## Approach
- **Controllers**: `BookingController` and `RoomController` expose REST endpoints for booking creation, single booking lookup, cancellation, and daily room schedules.
- **Service Layer**: `BookingService` orchestrates field validations, checks room capacity against attendees (automatically ensuring the organizer is an attendee), and validates time intervals before executing overlap detection.
- **Repositories**: `RoomRepository` and `BookingRepository` query entities in H2. Overlaps are detected using interval logic: `(existingStart < newEnd) AND (existingEnd > newStart) AND (cancelled = false)`.
- **Global Error Handling**: `GlobalExceptionHandler` intercepts exceptions and produces a unified `ApiErrorResponse` JSON across 400 (validation), 404 (not found), and 409 (conflict naming clashing booking).
- **Startup Seeding**: `DataSeeder` seeds 6 initial rooms (names, capacities, active flags).

## Decisions & trade-offs
- **Cancellation via Soft Status**: Cancelled bookings retain their record with `cancelled = true` rather than being hard deleted. This preserves audit history while immediately freeing up the time slot for new bookings.
- **Flexible Room Resolution**: `CreateBookingRequest` accepts either `roomId` or `roomName` for convenience.
- **Interval Overlap Arithmetic**: Using `(startTime < newEnd) AND (endTime > newStart)` accurately allows back-to-back bookings (e.g. 10:00–11:00 and 11:00–12:00) while rejecting partial overlaps and fully enveloped bookings.
- **In-Memory H2 DB**: Selected for fast, deterministic automated test execution without external dependencies.
- **Injectable Clock**: `Clock` bean injected into services enables reproducible testing of past vs future booking validations.

## How to test
- **Run Automated Test Suite**:
  ```bash
  ./mvnw test
  ```
- **Test Class**: `com.example.meetingroom.MeetingRoomIntegrationTests`
  - Validates acceptance criteria: 10:00–11:00 followed by 10:30–11:30 returns 409.
  - Validates back-to-back booking 11:00–12:00 returns 201.
  - Validates enveloping booking 09:00–12:00 over 10:00–10:30 is rejected (409).
  - Validates unblocking room after cancellation.
  - Validates working hours, 15-minute steps, attendee capacity, and 404/400 errors.
- **Run Application Server**:
  ```bash
  ./mvnw spring-boot:run
  ```
- **Sample Request (Create Booking)**:
  ```http
  POST /api/bookings
  Content-Type: application/json

  {
    "roomId": 1,
    "title": "Quarterly Strategy Review",
    "organiserEmail": "alex@company.com",
    "attendeeEmails": ["alex@company.com", "dev@company.com"],
    "startTime": "2026-10-16T10:00:00",
    "endTime": "2026-10-16T11:00:00"
  }
  ```
