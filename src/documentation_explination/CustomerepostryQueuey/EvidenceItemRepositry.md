# EvidenceItemRepository Custom Queries Explanation

## Why Custom Queries in EvidenceItemRepository?

While standard Spring Data JPA provides automatic CRUD operations (like `save`, `findById`, `delete`), evidence management in a courtroom evidence vault requires two specialized, high-performance database queries that standard method names cannot satisfy.

---

## 1. `findByCaseWithFilters`: Multi-Filter Case Evidence Search

```java
@Query("SELECT e FROM EvidenceItem e WHERE " +
        "e.legalCase.id = :caseId AND " +
        "(:status IS NULL OR e.status = :status) AND " +
        "(:type IS NULL OR e.evidenceType = :type)")
Page<EvidenceItem> findByCaseWithFilters(
        @Param("caseId") Long caseId,
        @Param("status") EvidenceStatus status,
        @Param("type") EvidenceType type,
        Pageable pageable
);
```

### What It Does:
* In the frontend evidence vault dashboard, an attorney or examiner might want to:
  * View all evidence in Case #42.
  * Filter to view only `AVAILABLE` evidence.
  * Filter to view only `DIGITAL_IMAGE` evidence.
  * Combine filters (e.g., only `IN_LAB` and `FORENSIC_EXTRACTION` items).

### Why the `(:status IS NULL OR ...)` Pattern Matters:
* In SQL, if the user doesn't select a status filter, the parameter passed is `null`.
* The condition `(:status IS NULL OR e.status = :status)` ensures that if `status` is null, the query evaluates to `TRUE` and skips the filter entirely without failing or returning 0 results.
* This allows a single, elegant query to handle all 4 filter combinations dynamically without writing multiple spaghetti `if-else` repository methods.

---

## 2. `findByIdWithPessimisticLock`: The Concurrency Serialization Engine

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT e FROM EvidenceItem e WHERE e.id = :id")
Optional<EvidenceItem> findByIdWithPessimisticLock(@Param("id") Long id);
```

### What It Does:
* This is the cornerstone of our **Unit 3 Concurrency Architecture**.
* When Spring executes this query against PostgreSQL, it appends:
  ```sql
  SELECT * FROM evidence_items WHERE id = ? FOR UPDATE;
  ```

### Why Pessimistic Locking (`PESSIMISTIC_WRITE`) is Critical:
* In physical evidence vaults, two examiners cannot hold the same laptop or drive at the same time.
* If Examiner 1 and Examiner 2 click "Book Reservation" at the exact same millisecond:
  * Without this lock, both threads read that the slot is open simultaneously and both create a booking (a double-booking race condition).
  * **With this lock**, the database immediately locks the evidence row for Examiner 1. Examiner 2's thread is paused until Examiner 1's transaction commits.
  * When Examiner 2 is unpaused, the booking interval query re-evaluates and rejects Examiner 2 with HTTP `409 Conflict`.
