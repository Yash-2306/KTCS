# KTCS — School Management System: Complete Technical Reference

## What This Document Is

This document covers every technical decision made in this project, how each technology works, why it was chosen, and exactly how you should explain it in an interview. Everything here is grounded in the actual code — not theory.

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Full Architecture](#2-full-architecture)
3. [Backend — Spring Boot](#3-backend--spring-boot)
4. [Security Layer — JWT, BCrypt, Filters](#4-security-layer)
5. [Database — MySQL, JPA, Hibernate](#5-database--mysql-jpa-hibernate)
6. [API Design — REST Endpoints](#6-api-design--rest-endpoints)
7. [Frontend — HTML, Tailwind, Vanilla JS](#7-frontend)
8. [PDF Generation — OpenPDF](#8-pdf-generation)
9. [Deployment — Railway, Netlify](#9-deployment)
10. [Performance & Scaling Decisions](#10-performance--scaling)
11. [Interview Questions — Exact Answers](#11-interview-questions)

---

## 1. System Overview

**What it is**: A full-stack web application for a real school (Kriti The Concept School, Hyderabad). It manages students, teachers, daily attendance, marks/grades, report cards, walk-in admission inquiries, announcements, timetables, and analytics — all live in production.

**Who uses it**:
- **Admin** — manages everything: adds students/teachers, views all data, downloads reports
- **Teachers** — mark daily attendance for their class, enter subject marks
- **Students** — view their own report card, attendance calendar, announcements

**Scale**: ~300+ students, ~20 teachers, daily operational use.

**Tech stack summary**:

| Layer | Technology | Deployed On |
|---|---|---|
| Frontend | HTML + Tailwind CSS + Vanilla JS | Netlify |
| Backend | Java 17 + Spring Boot 3.x | Railway |
| Database | MySQL 8 | Railway (managed) |
| Auth | Custom JWT (JJWT library) | Embedded in backend |
| PDF | OpenPDF | In-memory, no storage |

---

## 2. Full Architecture

```
BROWSER (any device)
    |
    |-- HTTPS --> Netlify CDN
    |             [Static Files: HTML, CSS, JS]
    |             index.html, Login.html,
    |             admin-dashboard.html,
    |             teacher-dashboard.html,
    |             student-dashboard.html
    |
    |-- REST API calls (HTTPS + JWT) --> Railway Server
                                          |
                                    Spring Boot App
                                          |
                    +---------------------+---------------------+
                    |                     |                     |
              JwtFilter             RateLimiter           WebConfig
              (runs on ALL          (30 req/min           (CORS rules:
               requests)            per IP)                allow Netlify)
                    |
              Controllers (REST)
                    |
         +----------+-----------+----------+-----------+
         |          |           |          |           |
     AuthCtrl  AttendCtrl   MarkCtrl  LeadCtrl  VisitorCtrl
         |          |           |          |           |
         +----------+-----------+----------+-----------+
                    |
              JPA Repositories
              (Spring Data)
                    |
              MySQL Database (Railway)
              [Tables: users, attendance, marks,
               leads, left_users, visitor_stats,
               audit_logs, announcements, timetable]
```

**Key architectural principle**: The frontend and backend are completely decoupled. The frontend is a static site. It communicates with the backend only via REST API calls carrying a JWT in the `Authorization` header. No server-side rendering.

---

## 3. Backend — Spring Boot

### What Spring Boot is

Spring Boot is a Java framework that auto-configures a web server (embedded Tomcat), dependency injection, database connection pooling, and dozens of other things so you don't have to write boilerplate. You write your logic; Spring Boot wires everything together.

### The Layered Architecture Pattern

The backend follows a 3-layer pattern:

```
HTTP Request
    ↓
Controller   ← receives request, validates input, returns response
    ↓
Service      ← (some controllers contain logic directly; no separate service layer was added)
    ↓
Repository   ← talks to the database via JPA
    ↓
Database
```

### Dependency Injection

Spring manages a container of objects called "beans." When you annotate a class with `@Component`, `@Service`, `@Repository`, or `@RestController`, Spring creates one instance and injects it wherever it's needed via `@Autowired`.

```java
// Example from AuthController:
@Autowired
private UserRepository userRepository;

@Autowired
private JwtUtil jwtUtil;
```

Spring finds `UserRepository` and `JwtUtil` in its container and passes them in. You never write `new UserRepository()`.

### Application Entry Point

```java
@SpringBootApplication  // combines @Configuration + @EnableAutoConfiguration + @ComponentScan
public class SchoolApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchoolApplication.class, args);
    }
}
```

`@SpringBootApplication` tells Spring to scan the package for beans and auto-configure everything it finds.

### application.properties — Key Configurations

```properties
spring.datasource.url=jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=update   # auto-creates/updates tables from entity classes
spring.datasource.hikari.maximum-pool-size=5  # connection pool limit
JAVA_TOOL_OPTIONS=-Xmx300m              # JVM heap cap at 300MB
```

**Why `ddl-auto=update`**: On first run, Hibernate reads your `@Entity` classes and creates the matching MySQL tables automatically. On subsequent runs, it only adds new columns — never drops existing data. This saves writing manual SQL `CREATE TABLE` statements.

**Why `maximum-pool-size=5`**: Railway's free/hobby tier has limited memory. A pool of 5 database connections is enough for a school with ~300 users and is well within resource limits.

---

## 4. Security Layer

### How the Whole Auth Flow Works (Step by Step)

```
1. User opens Login.html and enters username/password

2. Browser sends:
   POST /api/auth/login
   { "username": "admin", "password": "abc123" }

3. AuthController receives the request
   - Looks up user in database by username
   - If not found → 401 error
   - If account locked (failedAttempts >= 5 and lockTime < 15 mins ago) → 423 error
   - Calls BCrypt.matches(rawPassword, hashedPassword)
   - If wrong password → increment failedAttempts → if >= 5, set lockTime
   - If correct → reset failedAttempts → generate JWT token

4. Server responds:
   { "token": "eyJhbGciOiJIUzI1NiJ9...", "role": "ADMIN", "userId": 1, "fullName": "..." }

5. Browser stores token in localStorage

6. For every subsequent API call, browser sends:
   GET /api/attendance/students
   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

7. JwtFilter (runs BEFORE the controller):
   - Reads the Authorization header
   - Extracts the token
   - Calls jwtUtil.isValid(token)
   - If invalid/expired → 401 Unauthorized
   - If valid → extract username, role, userId and attach to request

8. Controller receives the request with user context attached
```

> Note: The login endpoint also verifies that the `role` field in the request matches the user's actual role in the database. So a student cannot log in by selecting "Admin" on the login screen — both password AND role must be correct.

### JWT (JSON Web Token) — Deep Dive

A JWT has 3 parts separated by dots:

```
eyJhbGciOiJIUzI1NiJ9  .  eyJ1c2VybmFtZSI6ImFkbWluIiwicm9sZSI6IkFETUlOIiwidXNlcklkIjoxLCJpYXQiOjE2OTk5OTk5OTksImV4cCI6MTcwMDA4NjM5OX0  .  signature
    HEADER                                          PAYLOAD                                                                                         SIGNATURE
```

- **Header**: algorithm used (HS256 = HMAC-SHA256)
- **Payload**: the claims — `{ username, role, userId, iat (issued at), exp (expires) }`
- **Signature**: HMAC-SHA256(base64(header) + "." + base64(payload), SECRET_KEY)

The signature is the security mechanism. Only the server knows the SECRET_KEY, so nobody can forge a token. When the server receives a token, it recomputes the signature and checks it matches.

**Why JWT instead of sessions**:
- Sessions require the server to store state (session store). JWT is stateless — the server stores nothing. Every piece of needed information is in the token itself.
- Stateless = scales horizontally without shared session storage.
- Works naturally across domains (Netlify frontend → Railway backend).

**From JwtUtil.java** — token expires in **8 hours**:
```java
private static final long EIGHT_HOURS_MS = 8 * 60 * 60 * 1000L;

public String generateToken(Long userId, String username, String role) {
    return Jwts.builder()
        .subject(username)
        .claim("userId", userId)
        .claim("role", role)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + EIGHT_HOURS_MS))
        .signWith(signingKey)
        .compact();
}
```

The secret key is read from the `JWT_SECRET` environment variable at startup. If not set (local dev), a fallback is used and a warning is printed. The key must be at least 32 characters for HS256.

### BCrypt Password Hashing

When a user account is created:
```java
String hashed = new BCryptPasswordEncoder().encode("rawPassword");
// hashed looks like: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LTlluAba9Jm
user.setPasswordHash(hashed);
```

When verifying login:
```java
new BCryptPasswordEncoder().matches("rawPassword", storedHash) // returns true/false
```

**Why BCrypt**:
- It's a one-way hash — you cannot reverse it to get the original password
- It automatically includes a random "salt" (the `$2a$10$N9qo...` prefix encodes the salt)
- The same password hashed twice gives different results, so rainbow table attacks fail
- It's deliberately slow (10 rounds) to make brute-forcing impractical

### Account Lockout Mechanism

From the User entity:
```java
private int failedLoginAttempts;      // counter
private LocalDateTime accountLockedUntil;  // when the lock expires
```

From AuthController:
```java
if (user.getFailedLoginAttempts() >= 5) {
    if (user.getAccountLockedUntil() != null
        && LocalDateTime.now().isBefore(user.getAccountLockedUntil())) {
        // Still locked — reject
        return ResponseEntity.status(423).body("Account locked. Try again in 15 minutes.");
    } else {
        // Lock expired — reset counter
        user.setFailedLoginAttempts(0);
    }
}
// After wrong password:
user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
if (user.getFailedLoginAttempts() >= 5) {
    user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(15));
}
```

This protects against brute-force attacks where an attacker tries thousands of passwords.

### JwtFilter — The Security Guard

```java
@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_ROUTES = List.of(
        "/api/auth/login",
        "/api/leads",
        "/api/visitors"   // public pages fire this without tokens
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) {
        String path = request.getRequestURI();

        // Let CORS preflight pass
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Let public routes pass without token
        if (PUBLIC_ROUTES.stream().anyMatch(path::startsWith)) {
            chain.doFilter(request, response);
            return;
        }

        // All other routes: require valid JWT
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            return;
        }

        String token = authHeader.substring(7); // strip "Bearer "
        if (!jwtUtil.isValid(token)) {
            response.setStatus(401);
            return;
        }

        // Token valid — attach claims to request for controllers to read
        Claims claims = jwtUtil.parseToken(token);
        request.setAttribute("username", claims.getSubject());
        request.setAttribute("role", claims.get("role", String.class));
        request.setAttribute("userId", claims.get("userId", Long.class));

        chain.doFilter(request, response); // continue to controller
    }
}
```

`OncePerRequestFilter` ensures this runs exactly once per request even in complex filter chains.

### SecurityConfig

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())  // disabled because we use JWT (stateless), not cookies
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

**Why CSRF is disabled**: CSRF (Cross-Site Request Forgery) attacks exploit browser cookies. Since we use JWT in headers (not cookies), CSRF is not a threat.

**Why `SessionCreationPolicy.STATELESS`**: Tells Spring Security never to create HttpSessions. Each request must be self-contained with its JWT.

### CORS — Cross-Origin Resource Sharing

The frontend is on `ktcs.netlify.app`. The backend is on `ktcs-backend-production.up.railway.app`. These are different origins. Browsers block cross-origin requests by default. CORS headers tell the browser: "This server allows requests from that origin."

```java
// WebConfig.java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("https://ktcs.netlify.app", "http://localhost:3000")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }
}
```

### Rate Limiting

```java
// RateLimitInterceptor.java
// Limits each IP to 30 requests per minute
// Uses a ConcurrentHashMap: IP → [request timestamps]
// If the count of timestamps in the last 60 seconds exceeds 30 → return 429 Too Many Requests
```

This protects the server from DoS (denial of service) attacks.

---

## 5. Database — MySQL, JPA, Hibernate

### How JPA and Hibernate Work

**JPA (Java Persistence API)** is a specification — a set of rules and interfaces for mapping Java objects to database tables.

**Hibernate** is the implementation of JPA that Spring Boot uses by default.

You write a Java class annotated with `@Entity`, and Hibernate automatically generates the SQL to create and query the corresponding table.

### User Entity — Complete Example

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // auto-increment primary key
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role;  // "ADMIN", "TEACHER", "STUDENT"

    private String fullName;
    private String email;
    private String phone;
    private String className;
    private String section;
    private String subject;     // for teachers

    private int failedLoginAttempts;
    private LocalDateTime accountLockedUntil;
    private LocalDateTime createdAt;

    // getters and setters...
}
```

Hibernate sees this class and creates:
```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    email VARCHAR(255),
    ...
);
```

### Spring Data JPA Repositories

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByRole(String role);
    List<User> findByClassNameAndSection(String className, String section);
}
```

Spring auto-generates the SQL from the method name:
- `findByUsername` → `SELECT * FROM users WHERE username = ?`
- `findByClassNameAndSection` → `SELECT * FROM users WHERE class_name = ? AND section = ?`

You write zero SQL. Spring reads the method name and builds the query.

### Custom Native Queries

For more complex operations, you can write SQL directly:

```java
// VisitorStatsRepository.java
@Modifying
@Transactional
@Query(value = "INSERT INTO visitor_stats (page_name, hit_count) " +
               "VALUES (:pageName, 1) " +
               "ON DUPLICATE KEY UPDATE hit_count = hit_count + 1",
       nativeQuery = true)
void incrementCount(@Param("pageName") String pageName);
```

`ON DUPLICATE KEY UPDATE` is a MySQL-specific clause. If the `page_name` already exists (primary key conflict), instead of throwing an error it runs the UPDATE part. This is an atomic "upsert" — no race condition where two concurrent hits could both try to insert and one fails.

### Database Indexes

```java
// Added to the User entity for performance:
@Table(name = "users", indexes = {
    @Index(name = "idx_class_section", columnList = "className, section")
})
```

An index creates a B-tree data structure that lets MySQL find rows by `className` and `section` without scanning every row. When a teacher loads attendance for "Class 10 Section A", MySQL uses this index instead of a full table scan. With 300+ students this matters.

### Entity Relationships

```
User (1) ←→ (many) Attendance
  - One student has many attendance records
  - studentId (Long) stored in Attendance — it's a foreign key but stored as a raw Long,
    not a JPA @ManyToOne. This is simpler for this use case.

User (1) ←→ (many) Mark
  - One student has many marks (one per subject per exam type)
```

### All Database Tables

| Table | Purpose |
|---|---|
| `users` | All accounts: ADMIN, TEACHER, STUDENT |
| `attendance` | Daily attendance records per student |
| `marks` | Subject marks per student per exam type |
| `leads` | Walk-in admission inquiries |
| `left_users` | Archive of deleted/left users |
| `visitor_stats` | Page hit counter (main site, dashboards) |
| `audit_logs` | Admin action history |
| `announcements` | Broadcast messages by role |
| `timetable` | Class schedule entries |

---

## 6. API Design — REST Endpoints

### REST Principles Applied

**REST** (Representational State Transfer) is a style for designing APIs using standard HTTP methods:

| HTTP Method | Meaning | Example in this project |
|---|---|---|
| GET | Read data | `GET /api/attendance/history/{studentId}` |
| POST | Create new data | `POST /api/auth/login` |
| PUT | Update existing data | `PUT /api/leads/{id}/status` |
| DELETE | Delete data | `DELETE /api/users/{id}` |

### All Endpoints — Complete List

**Authentication**
```
POST   /api/auth/login                          → validates credentials, returns JWT
POST   /api/auth/register                       → creates new user account (admin only)
POST   /api/auth/bulk-import                    → CSV bulk user creation
GET    /api/auth/students                       → list all students
GET    /api/auth/teachers                       → list all teachers
GET    /api/auth/students/class/{class}/{sec}   → students by class+section
DELETE /api/auth/users/{id}                     → delete user
PUT    /api/auth/users/{id}/password            → reset password
```

**Attendance**
```
POST   /api/attendance/submit                   → teacher submits attendance list
GET    /api/attendance/history/{studentId}      → student's full attendance history
GET    /api/attendance/summary/{studentId}      → present/absent/leave counts
GET    /api/attendance/class/{class}/{section}/{date}  → class attendance on a date
```

**Marks**
```
POST   /api/marks/submit                        → teacher enters marks for a student
GET    /api/marks/student/{id}/report-card      → all marks + computed grade/percentage
GET    /api/marks/student/{id}/report-card/pdf  → PDF download of report card
GET    /api/marks/class/{class}/{section}       → all students' marks for a class
```

**Leads (Walk-in Inquiries)**
```
POST   /api/leads                               → public: submit inquiry form
GET    /api/leads                               → admin: list all inquiries
PUT    /api/leads/{id}/status                   → admin: update lead status
DELETE /api/leads/{id}                          → admin: delete lead
```

**Visitor Stats**
```
POST   /api/visitors/hit?page=MAIN_SITE         → public: record a page hit
GET    /api/visitors/stats                      → admin: get all hit counts
```

**Salary, Announcements, Timetable** — similar CRUD patterns.

### JSON Request/Response Format

Login request:
```json
{ "username": "john_teacher", "password": "securepass123" }
```

Login response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "TEACHER",
  "userId": 42,
  "fullName": "John Smith",
  "username": "john_teacher"
}
```

Attendance submission:
```json
{
  "date": "2025-07-21",
  "className": "10",
  "section": "A",
  "records": [
    { "studentId": 101, "status": "PRESENT" },
    { "studentId": 102, "status": "ABSENT" },
    { "studentId": 103, "status": "LEAVE" }
  ]
}
```

### ResponseEntity — How Responses Are Controlled

```java
// 200 OK with body
return ResponseEntity.ok(user);

// 201 Created
return ResponseEntity.status(201).body(savedUser);

// 401 Unauthorized
return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));

// 423 Locked
return ResponseEntity.status(423).body(Map.of("error", "Account locked"));
```

`@RestController` = `@Controller` + `@ResponseBody`. Every return value is automatically serialized to JSON by Jackson (the JSON library Spring Boot includes by default).

---

## 7. Frontend

### Architecture — Why Static + Fetch

The entire frontend is plain HTML files. No React, no Angular. This was a deliberate choice:
- Faster to build and debug
- Zero build pipeline — just edit and open in browser
- Netlify hosts static files for free with global CDN
- Works on any device without a framework dependency

### Authentication in the Browser

After login, the JWT is stored in `localStorage`:
```javascript
localStorage.setItem('authToken', data.token);
localStorage.setItem('role', data.role);
localStorage.setItem('userId', data.userId);
```

Every API call includes the token:
```javascript
async function authFetch(url, options = {}) {
    const token = localStorage.getItem('authToken');
    return fetch(url, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + token,
            ...options.headers
        }
    });
}
```

### Role-Based Redirect

At the top of each dashboard:
```javascript
if (localStorage.getItem('role') !== 'ADMIN') {
    window.location.href = 'Login.html';
}
```

This prevents unauthorized access to dashboards. Note: this is client-side protection only for UX. The real protection is the backend rejecting API calls without a valid JWT.

### Sidebar + Panel Navigation

Each dashboard uses a single-page-application pattern without routing:
```javascript
function switchPanel(id) {
    document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    document.getElementById('panel-' + id).classList.add('active');
    document.getElementById('nav-' + id).classList.add('active');
    // lazy-load data when panel becomes active
    if (id === 'attendance') loadAttendanceStats();
}
```

Only one `.panel` is visible at a time via CSS: `.panel { display: none; }` and `.panel.active { display: block; }`.

### Visitor Tracking

On `index.html` and `Login.html` page load (before any login), the page fires:
```javascript
(function() {
    fetch('https://ktcs-backend-production.up.railway.app/api/visitors/hit?page=MAIN_SITE', {
        method: 'POST'
    }).catch(() => {});
})();
```

This is an IIFE (Immediately Invoked Function Expression) so it runs immediately. The `.catch(() => {})` silently ignores any errors — the site still works even if tracking fails. The `/api/visitors` route is in the JWT filter's public whitelist so it works without a token.

### Tailwind CSS

Tailwind is a utility-first CSS framework. Instead of writing custom CSS classes, you compose utilities directly in HTML:
```html
<div class="flex items-center gap-4 bg-white rounded-2xl shadow-md p-6">
```

The CDN version (used here) includes all utilities. For production at scale you'd use the CLI to purge unused styles, but for a school dashboard the CDN is fine.

### CSV Bulk Import — Frontend Side

```javascript
async function bulkImport() {
    const file = document.getElementById('csv-file').files[0];
    const formData = new FormData();
    formData.append('file', file);

    const res = await authFetch('/api/auth/bulk-import', {
        method: 'POST',
        body: formData  // Note: do NOT set Content-Type header — browser sets it with boundary
    });
}
```

**Important**: When using `FormData`, never manually set `Content-Type: application/json`. The browser sets `Content-Type: multipart/form-data; boundary=----...` automatically with the correct boundary string.

---

## 8. PDF Generation

### How OpenPDF Works

OpenPDF is a fork of iText 2 (free/open-source). It creates PDF documents programmatically in Java.

```java
// MarkController.java — report card PDF generation
@GetMapping("/student/{studentId}/report-card/pdf")
public ResponseEntity<byte[]> downloadReportCardPdf(
        @PathVariable Long studentId,
        @RequestParam(required = false) String examType,
        HttpServletRequest request) {

    // 1. Fetch data from database
    List<Mark> marks = markRepository.findByStudentIdAndExamType(studentId, examType);

    // 2. Create PDF in memory
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Document doc = new Document();
    PdfWriter.getInstance(doc, baos);
    doc.open();

    // 3. Add content
    doc.add(new Paragraph("Kriti The Concept School"));
    doc.add(new Paragraph("Student Report Card"));
    // ... add table with marks ...

    doc.close();

    // 4. Stream bytes directly to client
    byte[] pdfBytes = baos.toByteArray();
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=ReportCard.pdf")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdfBytes);
}
```

**Key design decision**: The PDF is never written to disk. It's created in a `ByteArrayOutputStream` (RAM), converted to a byte array, and immediately sent to the client. No file storage needed. After the method returns, Java's garbage collector reclaims the memory.

---

## 9. Deployment

### Railway (Backend + Database)

Railway is a PaaS (Platform as a Service) — similar to Heroku. You push code to GitHub, Railway detects it's a Spring Boot Maven project, builds it into a JAR, and runs it.

**How Railway knows how to run it**: It runs `mvn package` to build `target/school-0.0.1-SNAPSHOT.jar`, then runs `java -jar target/school-0.0.1-SNAPSHOT.jar`.

**Environment variables on Railway**: Database credentials, JWT secret, and Java options are stored as Railway environment variables — never in the code. Spring Boot picks them up at runtime via `${DB_HOST}` syntax in application.properties.

**Railway MySQL**: Managed MySQL instance. Railway provides the connection URL. The application connects to it exactly like a local MySQL server.

### Netlify (Frontend)

Netlify hosts the `Frontend/` folder as a static site. It serves HTML/CSS/JS from a global CDN (Content Delivery Network — servers distributed worldwide). This means the page loads fast from anywhere.

**Continuous deployment**: When you push to GitHub, Netlify automatically re-deploys the frontend. Backend deployment on Railway is similarly automatic.

### JAVA_TOOL_OPTIONS=-Xmx300m

Set as a Railway environment variable. `-Xmx300m` caps Java's heap memory at 300 megabytes. Without this cap, the JVM might try to use all available memory, triggering Railway's OOM (out of memory) killer which crashes the container. 300MB is enough for this application's workload.

---

## 10. Performance & Scaling Decisions

| Decision | Reason |
|---|---|
| HikariCP pool size = 5 | Railway has limited RAM; 5 connections is sufficient for ~300 users with typical patterns |
| Database indexes on className+section | Teachers query attendance/marks by class and section constantly; index makes this fast |
| PDF generated in memory, not stored | No disk I/O, no storage costs, no cleanup needed |
| Native SQL upsert for visitor tracking | `ON DUPLICATE KEY UPDATE` is atomic; avoids race condition with concurrent page hits |
| JWT stateless auth | No server-side session storage; scales horizontally without sticky sessions |
| Separation of frontend and backend | Frontend can be cached on CDN globally; backend only handles API requests |

### HikariCP — Connection Pool

Opening a database connection takes ~50ms. A connection pool maintains open connections that are reused. When a request comes in, it grabs a connection from the pool, uses it, and returns it. With 5 in the pool, up to 5 requests can query the database simultaneously. Any beyond that wait briefly.

---

## 11. Interview Questions — Exact Answers

### Spring Boot / Java

**Q: What is Spring Boot and how is it different from Spring?**

Spring Framework is a comprehensive Java framework for building applications. It requires a lot of manual configuration — you set up the Tomcat server, configure beans, set up transactions, etc. Spring Boot adds "convention over configuration" — it auto-configures all of that based on what's on the classpath. If it sees MySQL on the classpath, it auto-configures a datasource. If it sees Spring MVC, it auto-configures an embedded Tomcat. You just write your business logic.

**Q: What is dependency injection and why does it matter?**

Dependency injection means that instead of a class creating its own dependencies with `new`, those dependencies are provided ("injected") by an external container. In this project, Spring manages all our `@Component` and `@Repository` beans. This decouples the classes — `AuthController` doesn't need to know how to create a `UserRepository`. It just declares it needs one, and Spring provides it. This makes testing easier (you can inject a mock repository in tests) and reduces coupling between classes.

**Q: Explain how `@Transactional` works.**

`@Transactional` wraps a method in a database transaction. If anything throws an exception, all database changes made in that method are rolled back automatically. Without it, if you write to two tables and the second fails, the first write remains — leaving the database in an inconsistent state. For the bulk-import feature, the whole CSV import is transactional so partial failures don't leave half-imported data.

**Q: What is the difference between `@Component`, `@Service`, `@Repository`, and `@Controller`?**

All four are Spring-managed beans. They're semantically different aliases:
- `@Component` — generic bean
- `@Service` — business logic layer
- `@Repository` — data access layer (also adds exception translation — converts database exceptions to Spring's DataAccessException hierarchy)
- `@Controller` / `@RestController` — web layer

Using the correct annotation communicates intent and enables Spring to apply layer-specific behavior.

### Security

**Q: How does JWT authentication work in your project?**

On login, the server validates credentials, then creates a JWT containing the username, role, and userId signed with a secret key using HMAC-SHA256. This token is returned to the client. For every subsequent request, the client includes this token in the `Authorization: Bearer <token>` header. Our `JwtFilter` intercepts every request, extracts the token, verifies the signature using the same secret key, and attaches the user's identity to the request. If the token is missing, expired, or has an invalid signature, we return 401. No server-side state is maintained.

**Q: Why is CSRF protection disabled?**

CSRF attacks exploit the browser's automatic cookie-sending behavior. An attacker's malicious page can make the victim's browser send requests to your site with the victim's session cookie attached. JWT stored in localStorage is not automatically sent by the browser — the JavaScript must explicitly add it to the Authorization header. Since we use JWT in headers rather than cookies, CSRF is not applicable.

**Q: What is BCrypt and why not use MD5 or SHA-256?**

BCrypt is a password hashing algorithm specifically designed for passwords. MD5 and SHA-256 are fast — a modern GPU can compute billions of hashes per second, making dictionary/brute-force attacks feasible. BCrypt is intentionally slow (configurable via cost factor/rounds — we use 10 rounds). It also automatically salts the hash — the salt is stored as part of the hash string, so the same password produces a different hash every time, defeating precomputed rainbow table attacks.

**Q: What happens when a user fails login 5 times in your system?**

The `failedLoginAttempts` counter on the User entity increments with each failed login. When it reaches 5, we set `accountLockedUntil` to the current time plus 15 minutes and save this to the database. On subsequent login attempts, we check if the current time is before `accountLockedUntil`. If so, we return HTTP 423 (Locked) with a message telling the user to wait. After 15 minutes, the check passes and we reset the counter on the next successful login. This is stored in the database so it persists across server restarts.

### Database / JPA

**Q: What is ORM and how does Hibernate work?**

ORM (Object-Relational Mapping) bridges Java objects and relational database tables. Hibernate maps Java classes annotated with `@Entity` to database tables and Java fields to columns. It handles INSERT/UPDATE/DELETE/SELECT generation automatically. You work with Java objects (`User user = userRepository.findById(1L)`) and Hibernate translates that to `SELECT * FROM users WHERE id = 1`.

**Q: What is the N+1 problem?**

If you have a list of 100 students and for each student you fire a separate query to get their marks, that's 1 query for students + 100 queries for marks = 101 queries. This is the N+1 problem. It's solved with `@OneToMany(fetch = FetchType.EAGER)` or by using `JOIN FETCH` in JPQL to load related data in a single query. In this project we avoided this by designing endpoints that fetch only what's needed for each view.

**Q: What is a database index and when did you use one?**

An index is a separate data structure (B-tree) that the database engine maintains alongside a table to speed up lookups on specific columns. Without an index, a query `WHERE class_name = '10' AND section = 'A'` would scan every row in the users table. With a composite index on `(class_name, section)`, MySQL jumps directly to matching rows. We added this index because teachers query students by class and section constantly — every attendance page load and marks page load triggers this query.

**Q: What is connection pooling?**

Opening a TCP connection to a database server takes time (typically 50-100ms). Connection pooling maintains a pool of already-open connections. When a request needs the database, it borrows one from the pool, uses it, and returns it. HikariCP is the default pool in Spring Boot. We configured it to a maximum of 5 connections to stay within Railway's resource limits. HikariCP is known for being the fastest Java connection pool due to its minimal overhead and use of lock-free data structures.

### Architecture & Design

**Q: Why did you separate the frontend and backend?**

Several reasons: First, the frontend can be deployed on a CDN (Netlify) which is globally distributed and free — static files load fast from any country. Second, the backend only handles API requests — no rendering HTML — so it uses less memory. Third, different deployment cycles — we can update the UI without redeploying the backend and vice versa. Fourth, the same backend API could serve a mobile app in the future without any changes.

**Q: What is a RESTful API?**

REST is an architectural style for web services using standard HTTP verbs. It's stateless (each request contains all needed information), uses resource-based URLs (`/api/users/42` not `/getUser?id=42`), and uses HTTP status codes meaningfully (200 OK, 201 Created, 401 Unauthorized, 404 Not Found). In this project, every API endpoint follows these conventions.

**Q: What is CORS and why did you need to configure it?**

CORS (Cross-Origin Resource Sharing) is a browser security policy. A web page from `ktcs.netlify.app` is not allowed to make JavaScript fetch requests to `ktcs-backend-production.up.railway.app` by default — they're different origins (different hostnames). The backend must explicitly tell the browser "requests from ktcs.netlify.app are allowed" via the `Access-Control-Allow-Origin` response header. We configured this in `WebConfig.java` using Spring's `CorsRegistry`. The browser first sends an OPTIONS "preflight" request to check if the cross-origin call is permitted, then sends the actual request if allowed.

**Q: How did you handle the visitor tracking concurrency issue?**

Visitor counts are incremented from many simultaneous page loads. A naïve approach (read count, add 1, write back) creates a race condition: two requests could both read "count = 5", both compute "5+1=6", and both write 6 — losing one increment. We solved this with MySQL's `ON DUPLICATE KEY UPDATE hit_count = hit_count + 1`. This is a single atomic SQL statement — the increment happens inside the database engine where it's protected by row-level locking. No application-level concurrency handling needed.

### Deployment / DevOps

**Q: How does your application get deployed to Railway?**

We push code to GitHub. Railway has a webhook set up on the repository. When it detects a new push, it pulls the code, runs `mvn package -DskipTests` to build the JAR file, then runs `java $JAVA_TOOL_OPTIONS -jar target/school-0.0.1-SNAPSHOT.jar`. Environment variables (database credentials, JWT secret) are stored in Railway's dashboard and injected into the JVM at runtime via `${VARIABLE_NAME}` references in `application.properties`.

**Q: Why did you set -Xmx300m?**

Railway's hobby plan gives containers a limited amount of RAM. By default, the JVM can use up to 25% of the system RAM it detects, which on some Railway configurations could be several gigabytes. If it tries to allocate that much, Railway's OOM killer terminates the container. `-Xmx300m` caps the Java heap at 300 megabytes, keeping the application within Railway's actual available memory and preventing crashes.

---

## Quick Reference — Technologies and Their Purpose

| Technology | What It Is | Why It's Used |
|---|---|---|
| Spring Boot | Java web framework | Eliminates boilerplate, auto-configures everything |
| Spring Security | Security framework | Provides filter chain for authentication/authorization |
| JPA / Hibernate | ORM for Java | Maps Java classes to database tables automatically |
| Spring Data JPA | Repository abstraction | Auto-generates SQL from method names |
| HikariCP | Database connection pool | Reuses connections; much faster than opening new ones |
| JJWT | JWT library | Creates and validates JSON Web Tokens |
| BCrypt | Password hashing | Secure, salted, slow — resistant to brute force |
| OpenPDF | PDF generation | Creates PDF documents in Java; open-source iText fork |
| MySQL | Relational database | Structured data storage with ACID guarantees |
| Tailwind CSS | Utility CSS framework | Fast UI development; no custom CSS needed |
| Lucide Icons | SVG icon library | Clean, consistent icon set via CDN |
| Railway | PaaS cloud platform | Hosts Java backend + MySQL; GitHub auto-deploy |
| Netlify | Static site CDN | Hosts HTML/CSS/JS; global CDN; free tier |
| Maven | Build tool | Manages dependencies, compiles, packages JAR |

---

*This document is based on the actual KTCS codebase as implemented. Every code snippet corresponds to real code in the project.*
