# be-hands-on-set3

Meeting room booking backend service built with Java 21, Spring Boot 3.4.3, Spring Data JPA, and H2 database.

## Problem
Calculates common free meeting slots across multiple attendees within working hours (09:00–18:00) with room recommendations, prevents double bookings under heavy concurrent load (even across multiple application instances), and supports safe idempotent retries.

## Approach
- **Concurrency & Double-Booking Prevention**: `BookingService` acquires a pessimistic write lock (`PESSIMISTIC_WRITE` / `SELECT ... FOR UPDATE`) on the `Room` entity row before verifying overlap conflicts and persisting bookings. This serializes concurrent booking attempts for the same room at the database level across multiple application instances, ensuring exactly one succeeds and the rest receive HTTP 409 Conflict.
- **Safe Retries & Idempotency**: Recognizes client retries either via the standard `Idempotency-Key` header or automatically by matching natural request attributes (`roomId`, `startTime`, `endTime`, `organiserEmail`). Retried requests return the previously created booking without creating duplicate records.
- **Common Free Slots Algorithm**: `FreeSlotsController` and `BookingService.findCommonFreeSlots`:
  1. Fetches all active non-cancelled bookings involving any of the requested attendees across all rooms on that date.
  2. Clamps busy intervals to working hours `[09:00, 18:00)`.
  3. Sorts and merges overlapping/contiguous busy periods into disjoint busy intervals.
  4. Identifies gaps of length $\ge D$ minutes.
  5. **Where to meet**: For each free slot, finds active rooms having sufficient capacity (`capacity >= groupSize`) that are completely free (`0 overlapping bookings`) for the entire duration of the slot.

## Decisions & trade-offs
- **Database-Level Pessimistic Locking over JVM Locks**: In-memory locks (like Java `ReentrantLock` or `synchronized`) fail when scaling horizontally across multiple app instances. Using `PESSIMISTIC_WRITE` on the `Room` entity row relies on the database's native row-level lock manager, guaranteeing safety across clustered instances while only serializing requests for the *same* room.
- **Dual Retry Recognition**: Supports both explicit `Idempotency-Key` headers (recommended by HTTP standards and payment gateways) and natural request signatures. This guarantees robustness against network dropped responses even if a client does not send custom headers.
- **Interval Merging for Multi-Attendee Calendars**: Merging busy intervals simplifies multi-person calendar arithmetic from $O(N \times M)$ comparisons down to a single linear sweep $O(N \log N)$ over sorted intervals.
- **Whole-Slot Room Availability**: Ensures recommended rooms are not just free at slot start/end, but unbooked for every minute of the candidate slot.

## How to test
- **Run Full Automated Test Suite (23 Tests)**:
  ```bash
  ./mvnw test
  ```
- **Test Classes**:
  - `MeetingRoomIntegrationTests`:
    - Acceptance criteria: 10:00–11:00 vs 10:30–11:30 returns 409; back-to-back 11:00–12:00 returns 201; enveloping booking 09:00–12:00 over 10:00–10:30 rejected.
    - Cancellation unblocks room availability.
    - Validations (working hours, 15-min steps, 4h max, single-day, capacity, inactive rooms).
  - `FreeSlotsAndConcurrencyTests`:
    - **Q2 Acceptance Criteria 1**: Attendees A (10:00–11:00, 09:30–10:30) & B (13:00–14:00, 13:15–13:45) with $D=30$ yields exactly `09:00–09:30`, `11:00–13:00`, `14:00–18:00`.
    - **Q2 Acceptance Criteria 2**: 20 simultaneous threads fire to book the same room at the same time: exactly 1 succeeds (201), 19 get 409, and database holds exactly 1 booking.
    - **Q2 Acceptance Criteria 3**: Retrying requests (via `Idempotency-Key` or repeated payload) returns the same booking without duplicates.
    - **Where to meet**: Rooms booked during part of a slot or smaller than attendee count are filtered out.
- **Run Application Server**:
  ```bash
  ./mvnw spring-boot:run
  ```
- **Sample Request (Find Free Slots)**:
  ```http
  POST /api/free-slots
  Content-Type: application/json

  {
    "date": "2026-10-16",
    "duration": 30,
    "attendees": ["alice@company.com", "bob@company.com"]
  }
  ```
