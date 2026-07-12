# School Management System (KTCS)

A full-stack web application built for school administration. It handles student and teacher management, daily attendance, grade tracking, report card generation, and walk-in inquiry tracking. It runs as a live production system.

---

## Architecture

```
                        +------------------+
                        |   Browser Client  |
                        |  (HTML/JS/Tailwind)|
                        +--------+---------+
                                 |
              +------------------+------------------+
              |                                     |
    +---------v----------+               +----------v---------+
    |   Public Pages      |               |  Protected Dashboards|
    | index.html          |               |  admin-dashboard.html|
    | Admissions.html     |               |  teacher-dashboard  |
    | Contact.html        |               |  student-dashboard  |
    | Login.html          |               +----------+---------+
    +---------------------+                          |
                                         JWT Token in localStorage
                                                     |
                                 +---------+---------+
                                 |   REST API (HTTPS)  |
                                 | Spring Boot Backend  |
                                 |  (Railway)           |
                                 +---------+-----------+
                                           |
                          +----------------+----------------+
                          |                |                |
                 +--------v------+ +-------v------+ +-------v------+
                 |  Auth Layer   | |  Controllers  | |  PDF Engine  |
                 |  JwtFilter    | |  Attendance   | |  OpenPDF     |
                 |  SecurityConfig| | Marks        | |  (streamed,  |
                 |  RateLimit    | |  Users        | |  no storage) |
                 +---------------+ |  Leads        | +--------------+
                                   |  Visitors     |
                                   |  Timetable    |
                                   |  Announcements|
                                   +-------+-------+
                                           |
                                  +--------v-------+
                                  |  MySQL Database |
                                  |  (Railway)      |
                                  +----------------+
```

---

## System Design Decisions

| Decision | Approach |
|---|---|
| Authentication | Stateless JWT — no server-side sessions |
| Password storage | BCrypt hashing |
| Brute-force protection | Account locked for 15 minutes after 5 failed login attempts |
| PDF generation | Rendered in-memory, streamed to client, never written to disk |
| Database access | Spring Data JPA with HikariCP connection pooling (pool size: 5) |
| Role-based access | Three roles — ADMIN, TEACHER, STUDENT — enforced at filter level |
| Visitor tracking | Native SQL upsert to handle concurrent page hits safely |

---

## Project Structure

```
KTCS/
├── Frontend/
│   ├── index.html              # Public landing page
│   ├── Login.html              # Authentication entry point
│   ├── Admissions.html         # Walk-in inquiry form
│   ├── Contact.html            # Contact form
│   ├── admin-dashboard.html    # Admin portal
│   ├── teacher-dashboard.html  # Teacher portal
│   ├── student-dashboard.html  # Student portal
│   └── Assets/                 # Images, fonts, static files
│
└── Backend/
    └── src/main/java/com/school/
        ├── controller/         # REST API endpoints
        ├── model/              # JPA entities (User, Attendance, Mark, ...)
        ├── repository/         # Spring Data JPA interfaces
        └── config/             # Security, JWT, rate limiting, CORS
```

---

## Features

- Admin portal: manage students, teachers, attendance, marks, leads, announcements, timetables, and visitor analytics
- Teacher portal: mark daily attendance, enter subject-wise marks, view announcements
- Student portal: view report card, attendance calendar, announcements, timetable
- Bulk CSV import for registering students and staff
- Automatic PDF report card generation per student per exam type
- Walk-in inquiry (lead) pipeline with status tracking
- Audit log recording admin actions
- Visitor hit counter aggregating traffic across all pages

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | HTML, Tailwind CSS, Vanilla JavaScript, Lucide icons |
| Backend | Java 17, Spring Boot 3.x, Spring Security |
| Database | MySQL 8 (Railway managed) |
| PDF Export | OpenPDF |
| Authentication | JWT (JJWT library) |
| Deployment | Netlify (Frontend), Railway (Backend + DB) |

---

## Environment Variables

| Variable | Description |
|---|---|
| `PORT` | Server port |
| `DB_HOST` | MySQL host address |
| `DB_PORT` | MySQL port |
| `DB_NAME` | Schema name |
| `DB_USER` | Database user |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | Secret key for signing JWT tokens |
