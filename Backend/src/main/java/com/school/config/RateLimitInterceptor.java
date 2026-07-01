package com.school.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimitInterceptor — Protects the /api/leads endpoint from spam.
 *
 * How it works (beginner-friendly explanation):
 * - Imagine a "bouncer" at the door that counts how many requests come from each IP address.
 * - If someone sends MORE than MAX_REQUESTS in one minute, the bouncer says "slow down" (429 error).
 * - The counter resets every minute automatically.
 *
 * This prevents someone from flooding the Contact form with thousands of fake messages.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    // Maximum number of requests allowed per IP address per minute
    private static final int MAX_REQUESTS_PER_MINUTE = 20;

    // How long one "window" lasts in milliseconds (60,000ms = 1 minute)
    private static final long WINDOW_MS = 60_000;

    // A simple in-memory table: IP address → [requestCount, windowStartTime]
    // ConcurrentHashMap is used because multiple users can hit the server at the same time
    private final ConcurrentHashMap<String, long[]> ipCounters = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // Get the IP address of whoever is making the request
        String ip = request.getRemoteAddr();

        long now = System.currentTimeMillis();

        // Get or create a counter for this IP: [count, windowStartTime]
        long[] counter = ipCounters.computeIfAbsent(ip, k -> new long[]{0, now});

        // If the 1-minute window has passed, reset the counter
        if (now - counter[1] > WINDOW_MS) {
            counter[0] = 0;       // reset count to zero
            counter[1] = now;     // start a new 1-minute window
        }

        // Increase the request count for this IP
        counter[0]++;

        // If the count exceeds the limit, block the request
        if (counter[0] > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(429); // 429 = Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\": \"Too many requests. Please wait a minute before trying again.\"}"
            );
            return false; // stops the request from reaching the controller
        }

        return true; // allows the request to proceed normally
    }
}
