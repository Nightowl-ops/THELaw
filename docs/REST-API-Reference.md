# VeritasVault  REST API Reference

Interactive Swagger Documentation: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

---

### Authentication & Users
1. POST Register User: [http://localhost:8080/api/auth/register](http://localhost:8080/api/auth/register)
2. POST Login User: [http://localhost:8080/api/auth/login](http://localhost:8080/api/auth/login)
3. GET Current User Profile: [http://localhost:8080/api/auth/me](http://localhost:8080/api/auth/me)

---

### Legal Cases
4. GET All Legal Cases: [http://localhost:8080/api/cases](http://localhost:8080/api/cases)
5. POST Create Legal Case: [http://localhost:8080/api/cases](http://localhost:8080/api/cases)
6. GET Legal Case By ID: [http://localhost:8080/api/cases/{caseId}](http://localhost:8080/api/cases/{caseId})
7. PATCH Update Case Status: [http://localhost:8080/api/cases/{caseId}/status](http://localhost:8080/api/cases/{caseId}/status)

---

### Evidence Items
8. POST Upload & Ingest Evidence: [http://localhost:8080/api/evidence/upload](http://localhost:8080/api/evidence/upload)
9. GET Evidence By ID: [http://localhost:8080/api/evidence/{evidenceId}](http://localhost:8080/api/evidence/{evidenceId})
10. GET All Evidence In Case: [http://localhost:8080/api/evidence/case/{caseId}](http://localhost:8080/api/evidence/case/{caseId})
11. GET Download Evidence Binary: [http://localhost:8080/api/evidence/{evidenceId}/download](http://localhost:8080/api/evidence/{evidenceId}/download)

---

### Custody Reservations
12. POST Create Custody Reservation: [http://localhost:8080/api/custody/reservations](http://localhost:8080/api/custody/reservations)
13. GET Custody Reservation By ID: [http://localhost:8080/api/custody/reservations/{reservationId}](http://localhost:8080/api/custody/reservations/{reservationId})
14. GET Reservations By Evidence ID: [http://localhost:8080/api/custody/reservations/evidence/{evidenceId}](http://localhost:8080/api/custody/reservations/evidence/{evidenceId})
15. GET Reservations By Examiner ID: [http://localhost:8080/api/custody/reservations/examiner/{examinerId}](http://localhost:8080/api/custody/reservations/examiner/{examinerId})
16. POST Checkout Evidence (IN_LAB): [http://localhost:8080/api/custody/reservations/{reservationId}/checkout](http://localhost:8080/api/custody/reservations/{reservationId}/checkout)
17. POST Checkin Evidence (AVAILABLE): [http://localhost:8080/api/custody/reservations/{reservationId}/checkin](http://localhost:8080/api/custody/reservations/{reservationId}/checkin)
18. POST Cancel Custody Reservation: [http://localhost:8080/api/custody/reservations/{reservationId}/cancel](http://localhost:8080/api/custody/reservations/{reservationId}/cancel)

---

### Chain of Custody Logs
19. GET All Custody Logs For Evidence: [http://localhost:8080/api/custody/logs/evidence/{evidenceId}](http://localhost:8080/api/custody/logs/evidence/{evidenceId})
20. GET Custody Log Entry By ID: [http://localhost:8080/api/custody/logs/{logId}](http://localhost:8080/api/custody/logs/{logId})

---

### Exhibit Binders
21. GET All Exhibit Binders: [http://localhost:8080/api/binders](http://localhost:8080/api/binders)
22. POST Create Exhibit Binder: [http://localhost:8080/api/binders](http://localhost:8080/api/binders)
23. GET Exhibit Binder By ID: [http://localhost:8080/api/binders/{binderId}](http://localhost:8080/api/binders/{binderId})
24. PATCH Update Binder Status: [http://localhost:8080/api/binders/{binderId}/status](http://localhost:8080/api/binders/{binderId}/status)
25. POST Add Exhibit To Binder: [http://localhost:8080/api/binders/{binderId}/exhibits](http://localhost:8080/api/binders/{binderId}/exhibits)
26. DELETE Remove Exhibit From Binder: [http://localhost:8080/api/binders/{binderId}/exhibits/{exhibitId}](http://localhost:8080/api/binders/{binderId}/exhibits/{exhibitId})

---

### Discovery Productions & Logs
27. POST Create Discovery Production: [http://localhost:8080/api/discovery/produce](http://localhost:8080/api/discovery/produce)
28. GET Download Outbound Discovery Package: [http://localhost:8080/api/discovery/download/{rawAccessToken}](http://localhost:8080/api/discovery/download/{rawAccessToken})
29. GET Discovery Delivery & Receipt Logs: [http://localhost:8080/api/discovery/{productionId}/receipts](http://localhost:8080/api/discovery/{productionId}/receipts)