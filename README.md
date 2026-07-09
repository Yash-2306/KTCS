# School Management System

This repository contains a full-stack school management system designed for administrative management, teacher portals, and student dashboards.

## Project Architecture

The system is designed around a decoupled client-server architecture:

```
[Client Dashboards] <--- REST API (JWT) ---> [Spring Boot Backend] <---> [MySQL Database]
```

1. **Frontend**: Static HTML, CSS, and Vanilla JavaScript. Styling is powered by Tailwind CSS. Icons are handled via Lucide. It is deployed as a single-page app architecture on Netlify.
2. **Backend**: Spring Boot application in Java, exposing REST endpoints. It handles business logic, security policies, token generation, and document export services. It is hosted on Railway.
3. **Database**: MySQL relational database storing entities for users, attendance, grades, timetables, and system logs.

## Core Features

- **Role-Based Access Control**: Separate, secure dashboards for Admin, Teacher, and Student roles.
- **Authentication**: Stateless session management using JWT (JSON Web Tokens) with a 24-hour validity window.
- **Security Protections**: 
  - Password hashing via BCrypt.
  - Brute-force protection that locks user accounts for 15 minutes after 5 consecutive failed login attempts.
  - Audit logging mapping critical admin operations (user registrations, deletions, locked accounts).
- **Academic Management**:
  - Class and section attendance logging.
  - Student exam grade entry and term evaluation.
  - Class-wise timetable mapping and daily announcement boards.
  - Dynamic, on-the-fly PDF report card generation streamed directly to clients.
- **Administrative Utilities**:
  - Walk-in inquiry lead tracking.
  - Bulk CSV import for fast student/staff enrollment.
  - Visitor counter tracking homepage and login portal statistics.

## Environment Variables

To launch the backend, the following variables must be configured:
- `PORT`: Network port for the server.
- `DB_HOST`: Database server IP or hostname.
- `DB_PORT`: Database server port.
- `DB_NAME`: Database name.
- `DB_USER`: Database login user.
- `DB_PASSWORD`: Database login password.
