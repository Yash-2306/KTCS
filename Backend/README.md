# School Management System - Backend

This directory houses the backend engine for the School Management System. It is constructed as a Spring Boot Java application exposing a RESTful API.

## Software Architecture

The backend implements a standard layered architecture to maintain clear separation of concerns:

- **Web Layer (Controllers)**: Receives HTTP requests, parses path parameters, processes JSON payloads, validates data structures, and return standard JSON responses.
- **Service/Logic Layer**: Applies business rules, processes calculations (e.g. salary computation, grade evaluations), and handles document conversion.
- **Access Layer (Repositories)**: Uses Spring Data JPA to communicate with the MySQL database. Uses transactional queries for safety.
- **Security Context (JwtFilter & SecurityConfig)**: Inspects the request header for `Authorization: Bearer <token>`, validates signatures, and maps the user's role to the execution context.

## Technology Stack

- **Framework**: Spring Boot 3.x
- **Security**: Spring Security (Stateless JWT configurations)
- **Database Mapping**: Hibernate / JPA
- **PDF Generation**: OpenPDF (fork of iText)
- **Database**: MySQL 8.x

## System Design and Optimizations

- **Resource Limits**: Configured HikariCP connection pool limit to 5 to run reliably on resource-limited server environments.
- **Memory Footprint**: Strict JVM heap sizing controls combined with dynamic PDF rendering (PDFs are generated, piped directly to the response output stream, and garbage collected immediately without saving to local disk or object storage).
- **Database Optimizations**: Built composite indexes on the student entity class and section fields to speed up search queries under concurrent teacher access.
- **Concurrency Safety**: Implemented database-level native upsert queries (`ON DUPLICATE KEY UPDATE`) for visitor tracking to prevent transaction deadlocks under concurrent traffic hits.
