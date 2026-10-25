# CustodyReservationRepository Custom Queries Explanation

## Why Custom Queries in CustodyReservationRepository?

The Golden Rule of Forensic Evidence: **Two examiners can never analyze or test the same physical evidence item at the same time.** 

Doing so would risk evidence contamination, cross-sample corruption, and create reasonable doubt in court regarding who caused any alterations.

---

## The Interval Collision Query: `findOverlappingReservations`

```java
@Query("SELECT r FROM CustodyReservation r " +
        "WHERE r.evidenceItem.id = :evidenceId " +
        "AND r.status IN (:activeStatuses) " +
        "AND (r.startTime < :requestedEnd AND r.endTime > :requestedStart)")
List<CustodyReservation> findOverlappingReservations(
        @Param("evidenceId") Long evidenceId,
        @Param("requestedStart") LocalDateTime requestedStart,
        @Param("requestedEnd") LocalDateTime requestedEnd,
        @Param("activeStatuses") List<ReservationStatus> activeStatuses
);
```

### The Mathematics of Time Overlap:
Why does `(r.startTime < :requestedEnd AND r.endTime > :requestedStart)` detect every possible time collision?

Suppose an existing booking runs from 10:00 AM to 12:00 PM:
1. **Completely Enclosed:** Examiner asks for 10:30 AM to 11:30 AM.
   * `10:00 < 11:30` (TRUE) and `12:00 > 10:30` (TRUE) -> **COLLISION DETECTED**
2. **Partial Overlap (Start):** Examiner asks for 9:00 AM to 10:30 AM.
   * `10:00 < 10:30` (TRUE) and `12:00 > 09:00` (TRUE) -> **COLLISION DETECTED**
3. **Partial Overlap (End):** Examiner asks for 11:30 AM to 1:00 PM.
   * `10:00 < 13:00` (TRUE) and `12:00 > 11:30` (TRUE) -> **COLLISION DETECTED**
4. **Completely Engulfing:** Examiner asks for 9:00 AM to 2:00 PM.
   * `10:00 < 14:00` (TRUE) and `12:00 > 09:00` (TRUE) -> **COLLISION DETECTED**
5. **Completely Before (No Collision):** Examiner asks for 8:00 AM to 9:30 AM.
   * `10:00 < 09:30` (FALSE) -> **NO COLLISION (APPROVED)**
6. **Completely After (No Collision):** Examiner asks for 1:00 PM to 3:00 PM.
   * `12:00 > 13:00` (FALSE) -> **NO COLLISION (APPROVED)**

This single boolean formula mathematically catches every single collision scenario without requiring multiple complex condition blocks.

---

## Status Filtering (`activeStatuses`)
* We only check against reservations that are currently active or confirmed (`CONFIRMED`, `ACTIVE`).
* Past or `CANCELLED` reservations do not block future bookings, ensuring examiner schedules remain flexible.
