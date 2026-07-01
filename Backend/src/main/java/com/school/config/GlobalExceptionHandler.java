package com.school.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Catches exceptions from any controller and returns a clean JSON error
 * instead of a raw Java stack trace. This is what the frontend will receive
 * when something goes wrong.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handles bad date formats like "2026-13-45" or "june2026"
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, String>> handleDateParseError(DateTimeParseException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Invalid date format. Please use YYYY-MM-DD (e.g. 2026-06-23)");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Handles bad number formats like passing "abc" for a numeric field
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<Map<String, String>> handleNumberFormatError(NumberFormatException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Invalid number format in request.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Handles illegal arguments (e.g., null pointer from bad request body)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage() != null ? ex.getMessage() : "Invalid request data.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Catch-all for any other unexpected error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralError(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Something went wrong on the server. Please try again.");
        // Print the real error to the server console for debugging
        System.err.println("Unhandled exception: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
