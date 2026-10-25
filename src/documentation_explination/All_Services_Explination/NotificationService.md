# NotificationService Explanation

## What is NotificationService?

In a high-security evidence vault, multiple users (attorneys, lead detectives, forensic examiners, system administrators) are operating simultaneously across the legal system.

If a forensic examiner returns a tampered evidence item or an attorney locks a trial binder, waiting minutes for users to manually refresh their web browsers is unacceptable. In legal custody, everyone needs **instant, real-time situational awareness**.

`NotificationService` provides real-time event streaming using **Server-Sent Events (SSE)**. It maintains open HTTP telemetry connections with active frontend clients, allowing the backend to immediately broadcast live events (such as evidence reservations, lab check-outs, hash verifications, and tamper alerts) to all connected screens without requiring client-side polling.

---

## Variables Being Used and Their Reasons:

* **`emitters`**: A thread-safe collection declared as:
  ```java
  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
  ```
  * **Why `CopyOnWriteArrayList`?** Multiple threads (HTTP request threads, scheduled tasks, and the 4 `labVerificationExecutor` worker threads) can broadcast alerts at the same millisecond. A standard `ArrayList` would throw `ConcurrentModificationException` when one thread sends an event while another client disconnects. `CopyOnWriteArrayList` is designed specifically for concurrent read-heavy subscriber lists.
* **`objectMapper`**: Jackson's JSON serializer injected by Spring Boot. Converts rich Java DTOs (like reservation records or lab report alerts) into valid JSON strings before broadcasting over the SSE stream.

---

## Methods and Their Implementation Explained:

### 1. `subscribe()`: [Establishing Client Telemetry]
* **Timeout Configuration:**
  * Creates an `SseEmitter(1800000L)` with a 30-minute keep-alive duration.
* **Registration:**
  * Adds the new emitter to the thread-safe `emitters` list.
* **Lifecycle Cleanup Hooks:**
  * `emitter.onCompletion(() -> emitters.remove(emitter));`
  * `emitter.onTimeout(() -> emitters.remove(emitter));`
  * `emitter.onError(e -> emitters.remove(emitter));`
  * **Why?** When a user closes their browser tab, refreshes the page, or loses Wi-Fi connection, these callbacks automatically detach the dead emitter from memory, preventing memory leaks.
* **Handshake Event:**
  * Sends an immediate welcome packet to acknowledge the connection:
    ```json
    event: CONNECTED
    data: "Subscribed to VeritasVault Real-Time Telemetry Stream"
    ```
* **Return:** Returns the `SseEmitter` object directly to `NotificationController`, keeping the HTTP response stream open.

### 2. `broadcast(String eventName, Object payload)`: [Multithreaded Event Dispatch]
* **JSON Serialization:**
  * Checks if `payload` is already a String; if not, uses `objectMapper.writeValueAsString(payload)` to serialize Java objects into JSON.
  * Catches formatting exceptions gracefully so a bad payload never crashes the broadcasting mechanism.
* **Broadcasting Loop:**
  * Iterates across all active client emitters in `emitters`.
  * Sends the named event formatted with `MediaType.APPLICATION_JSON`:
    ```java
    emitter.send(SseEmitter.event()
            .name(eventName)
            .data(jsonPayload, MediaType.APPLICATION_JSON));
    ```
* **Dead Connection Pruning:**
  * If a client abruptly severed their connection, `emitter.send(...)` throws `IOException` or `IllegalStateException`.
  * Catches the exception, collects the broken emitter in a `deadEmitters` list, and executes `emitters.removeAll(deadEmitters)` to keep the active pool clean.

---

## Critical Design Questions & Summary:

| Question | Answer |
| :--- | :--- |
| **Why Server-Sent Events (SSE) instead of WebSockets?** | SSE runs over standard HTTP/HTTPS, requires zero complex WebSocket handshake handoffs, automatically reconnects upon network drops, works behind standard reverse proxies, and is ideal for one-way server-to-client telemetry feeds. |
| **Why use `CopyOnWriteArrayList` instead of `Collections.synchronizedList`?** | In SSE architectures, reads (broadcasting to all listeners) occur far more frequently than writes (users logging in/out). `CopyOnWriteArrayList` provides lock-free, blazingly fast iteration without blocking threads. |
| **What happens when a client disconnects unexpectedly?** | The next broadcast attempt catches the broken pipe `IOException`, appends the dead emitter to `deadEmitters`, and removes it immediately to ensure no memory leak occurs. |
