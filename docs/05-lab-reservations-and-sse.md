# 05 - Forensic Lab Scheduling, Double-Booking Collision & Real-Time Telemetry

## 1. Functional Overview
The Custody Reservation module coordinates access to physical and digital forensic workstations. It prevents schedule collisions through overlapping interval queries and dispatches live telemetry events via Server-Sent Events (SSE) to connected clients.

When examiners work with critical evidence, concurrent checkouts can invalidate evidentiary custody. The system guarantees that evidence cannot be double-booked across conflicting intervals.

---

## 2. Relevant Source Files
- **Entities & Enums:**
    - `com.veritasvault.model.CustodyReservation`
    - `com.veritasvault.model.EvidenceItem`
    - `com.veritasvault.model.enums.ReservationStatus` (`PENDING`, `CONFIRMED`, `ACTIVE`, `COMPLETED`, `CANCELLED`)
    - `com.veritasvault.model.enums.EvidenceStatus` (`AVAILABLE`, `RESERVED`, `IN_LAB`, `SEQUESTERED`)
- **Repositories:**
    - `com.veritasvault.repository.CustodyReservationRepository`
    - `com.veritasvault.repository.EvidenceItemRepository`
- **Services:**
    - `com.veritasvault.service.CustodyReservationService`
    - `com.veritasvault.service.NotificationService`
- **Controllers & DTOs:**
    - `com.veritasvault.controller.CustodyReservationController`
    - `com.veritasvault.controller.NotificationController`
    - `com.veritasvault.dto.request.CustodyReservationRequest`
    - `com.veritasvault.dto.response.CustodyReservationResponse`

---

## 3. Workflow & Logic Sequence

### A. Laboratory Booking Collision Engine (`POST /api/custody/reservations`)
1. **Time Boundaries:**
    - Asserts `startTime.isBefore(endTime)`.
    - Asserts `startTime.isAfter(LocalDateTime.now())`. Past bookings throw `BadRequestException`.
2. **Evidence Integrity Check:**
    - Confirms evidence is not `SEQUESTERED`. Sequestered evidence cannot be reserved.
3. **Collision Detection Math:**
    - Queries `CustodyReservationRepository.findOverlappingReservations()` using the standard interval intersection logic:
      $$\text{Overlap} \iff (\text{existing.startTime} < \text{requested.endTime}) \land (\text{existing.endTime} > \text{requested.startTime})$$
    - Checked strictly against blocking statuses (`CONFIRMED`, `ACTIVE`).
    - If collisions exist, throws `BadRequestException` ("Time conflict: Evidence item is already booked...").
4. **State Transitions:**
    - Saves reservation as `CONFIRMED`.
    - Transitions `EvidenceItem.status` to `RESERVED`.
5. **SSE Broadcast:**
    - Fires `NotificationService.broadcast("CUSTODY_RESERVATION_CREATED", response)`.

### B. Evidence Lab Lifecycle (`CHECKOUT` -> `CHECKIN`)
[AVAILABLE] ──(Book Slot)──> [RESERVED] ──(Check-Out)──> [IN_LAB] ──(Check-In)──> [AVAILABLE]

1. **Check-out (`PATCH /api/custody/reservations/{id}/checkout`):**
    - Asserts reservation status is `CONFIRMED`.
    - Sets reservation to `ACTIVE`, evidence to `IN_LAB`.
    - Appends `CHECK_OUT` log to Chain of Custody ledger.
    - Fires SSE event: `EVIDENCE_CHECKED_OUT`.
2. **Check-in (`PATCH /api/custody/reservations/{id}/checkin`):**
    - Asserts reservation status is `ACTIVE`.
    - Sets reservation to `COMPLETED`, evidence to `AVAILABLE`.
    - Appends `CHECK_IN` log to Chain of Custody ledger with notes.
3. **Cancellation (`PATCH /api/custody/reservations/{id}/cancel`):**
    - Asserts reservation is neither `COMPLETED` nor already `CANCELLED`.
    - Confirms the calling user is the assigned examiner or an Admin.
    - Sets reservation to `CANCELLED`, releases evidence back to `AVAILABLE`.
    - Appends a cancellation `CHECK_IN` log to the Chain of Custody ledger.

### C. Server-Sent Events (SSE) Telemetry (`GET /api/notifications/stream`)
- Clients connect via standard SSE protocol (`text/event-stream`).
- Registered `SseEmitter` instances are tracked inside a thread-safe `CopyOnWriteArrayList`.
- Timeout is set to 30 minutes (1,800,000 ms).
- Broken or timed-out connections are cleaned up automatically via lifecycle callbacks (`onCompletion`, `onTimeout`, `onError`).
- When events fire (`broadcast(eventName, payload)`), the payload is converted to JSON via Jackson's `ObjectMapper` and pushed downstream to all active emitters.

---

## 4. API Endpoints

| HTTP Method | Endpoint | Required Role | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/custody/reservations` | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Books a lab slot with collision detection. |
| `PATCH` | `/api/custody/reservations/{id}/checkout` | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Checks out evidence for lab analysis. |
| `PATCH` | `/api/custody/reservations/{id}/checkin` | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Checks in evidence and marks analysis complete. |
| `PATCH` | `/api/custody/reservations/{id}/cancel` | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Cancels reservation and releases slot. |
| `GET` | `/api/custody/reservations/{id}` | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Fetches reservation details by ID. |
| `GET` | `/api/custody/reservations/evidence/{evidenceId}` | `ROLE_ATTORNEY`, `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Lists all reservations for a piece of evidence. |
| `GET` | `/api/custody/reservations/examiner/{examinerId}` | `ROLE_FORENSIC_EXAMINER`, `ROLE_ADMIN` | Lists reservations booked by an examiner. |
| `GET` | `/api/notifications/stream` | Public / Bearer | Establishes persistent real-time SSE telemetry connection. |

---

## 5. Business Rules & Edge Cases
- **Self-Cancellation Restriction:** An examiner can only cancel their own reservation. Only an Admin can cancel reservations made by other examiners.
- **Dead Emitter Cleanup:** When broadcasting events, if an emitter throws an `IOException` (e.g., client closed browser tab or disconnected network), it is caught, collected into a dead-emitter list, and removed from the active subscriber list.
- **Sequestered Lock:** An item flagged as `SEQUESTERED` is strictly locked out of the booking workflow until an authorized user resets its operational status.